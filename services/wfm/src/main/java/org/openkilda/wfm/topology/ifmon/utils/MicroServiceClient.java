/* Copyright 2017 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.wfm.topology.ifmon.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

public class MicroServiceClient {

    private final String tpnUsername;
    private final String tpnPassword;
    private long tokenExirationTime = 0;
    private String accessToken = null;

    private final String tpnEndpoint;
    private CloseableHttpClient httpClient;

    private ObjectMapper mapper = new ObjectMapper();
    private Map<String, PisSwitch> switchMapping = new HashMap<>();

    private static Logger logger = LoggerFactory.getLogger(MicroServiceClient.class);

    public MicroServiceClient(String tpnEndpoint, String tpnUsername, String tpnPassword) {
        this.tpnUsername = tpnUsername;
        this.tpnPassword = tpnPassword;
        this.tpnEndpoint = tpnEndpoint;

        try {
            SSLContext sslContext = new SSLContextBuilder().loadTrustMaterial(null, (certificate, authType) -> true)
                    .build();

            httpClient = HttpClients.custom()
                    .setDefaultHeaders(Arrays.asList(new BasicHeader(HttpHeaders.ACCEPT, "Application/json")))
                    .setSSLContext(sslContext).setSSLHostnameVerifier(new NoopHostnameVerifier()).build();
        } catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {
            logger.error("Failed to create micro service client", e);
        }

        logger.info("Successfully created micro service client");
    }
    
    
    /**
     * Get an authentication used for subsequent API calls.
     * 
     * @throws MicroServiceException Something wrong
     */
    public void getAuthToken() throws MicroServiceException {
        String getTokenUri = String.format("https://%s/1.0.0/auth/generatetoken", tpnEndpoint);
        HttpPost getTokenPost = new HttpPost(getTokenUri);

        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("grant_type", "password"));
        params.add(new BasicNameValuePair("username", tpnUsername));
        params.add(new BasicNameValuePair("password", tpnPassword));

        CloseableHttpResponse response = null;

        try {
            getTokenPost.setEntity(new UrlEncodedFormEntity(params));
            response = httpClient.execute(getTokenPost);

            String getTokenPayload = EntityUtils.toString(response.getEntity());
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                logger.error("Failed to get access token for user {} for reason {}", tpnUsername, getTokenPayload);
                throw new MicroServiceException("Failed to get acess token for user " + tpnUsername);
            }

            GetTokenResponsePayload payload = mapper.readValue(getTokenPayload, GetTokenResponsePayload.class);
            accessToken = payload.getAccessToken();
            tokenExirationTime = System.currentTimeMillis() + payload.getExpiresIn() * 1000;

            logger.info("Successfully retrieved access token for micro services");
        } catch (IOException e) {
            throw new MicroServiceException(e);
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                    //ignore
                }

            }
        }
    }
    
    /**
     * Calling PIS /switch api and construct a dpid to PisSwitchMapping.
     * 
     * @return A Map containing switch id (dpid) to PisSwitch Mapping
     * @throws MicroServiceException if something wrong
     */
    public Map<String, PisSwitch> getDpidSwitchMapping() throws MicroServiceException {

        Map<String, PisSwitch> switchMapping = new HashMap<>();
        String getSwitchUri = String.format("https://%s//pis/1.0.0/switch", tpnEndpoint);

        CloseableHttpResponse response = null;

        try {
            refreshAccessTokenIfNeeded();

            HttpGet getSwitchMethod = new HttpGet(getSwitchUri);
            getSwitchMethod.addHeader(HttpHeaders.AUTHORIZATION, String.format("Bearer %s", accessToken));
            response = httpClient.execute(getSwitchMethod);

            String responsePaylod = EntityUtils.toString(response.getEntity());
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                logger.error("Failed to get switch information from pis with reason", responsePaylod);
                throw new MicroServiceException("Failed to get switch information from pis");
            }

            PisGetSwitchResponsePayload payload = mapper.readValue(responsePaylod, PisGetSwitchResponsePayload.class);
            payload.getSwitches().stream().forEach(sw -> switchMapping.put(sw.getDpid(), sw));

            logger.debug("Got switch dpid/switch mapping:\n{}", responsePaylod);

        } catch (IOException e) {
            throw new MicroServiceException(e);
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                    //ignore
                }
            }
        }

        return switchMapping;
    }

    /**
     * Given a switch dpid, return the corresponding PisSwitch object.
     * @param switchDpid switch dpid, also known of switch id
     * @return PisSwitch object if found
     * @throws MicroServiceException if something wrong
     */
    public PisSwitch resolveSwitch(String switchDpid) throws MicroServiceException {
        PisSwitch sw = switchMapping.get(switchDpid);

        if (sw == null) {
            Map<String, PisSwitch> newMapping = getDpidSwitchMapping();
            // don't prematurely clear/reset the switch mapping, it might be just one
            // switch problem while the rest can still provide useful infomration
            switchMapping = newMapping;
            sw = switchMapping.get(switchDpid);
        }

        if (sw == null) {
            logger.error("Cannot find switch with dpid {} in lis", switchDpid);
            throw new MicroServiceException(String.format("Cannot find switch with dpid %s in lis", switchDpid));
        }

        return sw;
    }

    /**
     * Return PIS switch port detail.
     * 
     * @param switchDpid switch dpid also known as switch id
     * @param switchPort switch port no
     * @return  PIS Switch information
     * @throws MicroServiceException if something wrong
     */
    @Deprecated
    public IslPortResponse getSwitchPortDetail(String switchDpid, int switchPort)
            throws MicroServiceException {

        CloseableHttpResponse response = null;
        IslPortResponse payload = null;
        try {
            refreshAccessTokenIfNeeded();

            PisSwitch localSwitch = resolveSwitch(switchDpid);
            String localUuid = localSwitch.getUuid();

            String getSwitchPortDetailUrl = String.format("https://%s/pis/1.0.0/switchuuid/%s/port/%s", tpnEndpoint,
                    localUuid, Integer.toString(switchPort));

            HttpGet getSwitchMethod = new HttpGet(getSwitchPortDetailUrl);
            getSwitchMethod.addHeader(HttpHeaders.AUTHORIZATION, String.format("Bearer %s", accessToken));
            response = httpClient.execute(getSwitchMethod);

            String responsePaylod = EntityUtils.toString(response.getEntity());
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                String errMsg = String.format("Failed to get switch/port information from pis for %s(%s):%d",
                        switchDpid, localUuid, switchPort);
                logger.debug(errMsg + "\n{}", responsePaylod);
                throw new MicroServiceException(errMsg);
            }

            payload = mapper.readValue(responsePaylod, IslPortResponse.class);

            logger.debug("Successfully got {}:{} details:\n{}", switchDpid, switchPort, responsePaylod);

        } catch (IOException e) {
            throw new MicroServiceException(e);
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (IOException ignore) {
                    //ignore
                }
            }
        }

        return payload;
    }
    
    /**
     * Extract a concise description for a TPN switch/port.
     * 
     * @param sw PIS Switch object, containing switch basic information, like common name, etc.
     * @param switchPort the number of the port in question
     * @return a short description capturing the relevant information for the particular port type
     */
    public String getSwitchPortDetails(PisSwitch sw, int switchPort) throws MicroServiceException {
        
        CloseableHttpResponse response = null;
        
        try {
            refreshAccessTokenIfNeeded();
            
            String getSwitchPortDetailUrl = String.format("https://%s/pis/1.0.0/switchuuid/%s/port/%d", 
                    tpnEndpoint, sw.getUuid(), switchPort);
            
            HttpGet getSwitchPortMethod = new HttpGet(getSwitchPortDetailUrl);
            getSwitchPortMethod.addHeader(HttpHeaders.AUTHORIZATION, String.format("Bearer %s", accessToken));
            response = httpClient.execute(getSwitchPortMethod);
            
            String responsePayload = EntityUtils.toString(response.getEntity());
            EntityUtils.consume(response.getEntity());
            
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                String errMsg = String.format("Failed to get switch/port information from pis for %s(%s):%d", 
                        sw.getCommonName(), sw.getDpid(), switchPort);
                logger.debug(errMsg + "\n{}", responsePayload);
                throw new MicroServiceException(errMsg);
            }
            
            // Get a short description of the port according to the port type
            GenericPortReponse generic = mapper.readValue(responsePayload, GenericPortReponse.class);
            String description = null;
            PortAssignmentType assignment = PortAssignmentType.valueOf(generic.getAssignmentType());
            switch (assignment) {
                case ISL:
                    description = mapper.readValue(responsePayload, IslPortResponse.class).toString();
                    break;
                case CUSTOMER:
                    description = mapper.readValue(responsePayload, CustomerPortResponse.class).toString();
                    break;
                case VNF:
                    description = mapper.readValue(responsePayload, VnfPortResponse.class).toString();
                    break;
                case DIA:
                    description = mapper.readValue(responsePayload, DiaPortResponse.class).toString();
                    break;
                case EXCHANGE:
                    description = mapper.readValue(responsePayload, ExchangePortResponse.class).toString();
                    break;
                default:
                    //Just to silence checkstyle
            }
            
            return description;
        } catch (IOException e) {
            throw new MicroServiceException(e);
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (Exception e) {
                    //pass
                }
            }
        }
    }

    private void refreshAccessTokenIfNeeded() throws MicroServiceException {
        if (tokenExirationTime - System.currentTimeMillis() < TimeUnit.SECONDS.toMillis(10)) {
            logger.debug("Refreshing access token");
            getAuthToken();
        }
    }
    
    
    static enum PortAssignmentType {
        ISL("ISL"), CUSTOMER("Customer"), VNF("VNF"), DIA("DIA"), EXCHANGE("Exchange");
        
        private final String type;
        
        PortAssignmentType(String type) {
            this.type = type;
        }
        
        public String getType() {
            return type;
        }
    }
}

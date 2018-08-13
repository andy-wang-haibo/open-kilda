/* Copyright 2018 Telstra Open Source
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.ssl.SSLContextBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.net.ssl.SSLContext;

/**
 * Interfacing Alerta system.
 * 
 * @author andy
 *
 */
public class AlertaClient {

    private final String endpoint;
    private final String apiKey;
    private CloseableHttpClient httpClient;
    private ObjectMapper mapper = new ObjectMapper();

    private static final Logger logger = LoggerFactory.getLogger(AlertaClient.class);

    public AlertaClient(String endpoint, String apiKey) {
        this.endpoint = endpoint;
        this.apiKey = apiKey;

        try {
            // we don't check server certificate
            SSLContext sslContext = new SSLContextBuilder().loadTrustMaterial(null, (certificate, authType) -> true)
                    .build();

            httpClient = HttpClients.custom()
                    .setDefaultHeaders(Arrays.asList(
                            new BasicHeader(HttpHeaders.ACCEPT, "Application/json"),
                            new BasicHeader(HttpHeaders.AUTHORIZATION, "Key " + apiKey)))
                    .setSSLContext(sslContext).setSSLHostnameVerifier(new NoopHostnameVerifier()).build();
        } catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {
            logger.error("Failed to create micro service client", e);
            httpClient = null;
        }
    }

    /**
     * Create an alert in Alerta system.
     * 
     * @param payload Object representing Alerta create alert payload
     */
    public void createAlert(AlertaAlertPayload payload) {

        String url = String.format("%s/alert", endpoint);
        logger.debug("Using url {} for alert creation", url);

        CloseableHttpResponse response = null;
        try {
            HttpPost post = new HttpPost(url);
            post.addHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, "Application/json"));
            
            StringEntity entity = new StringEntity(mapper.writeValueAsString(payload));
            post.setEntity(entity);

            response = httpClient.execute(post);

            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_CREATED) {
                logger.error("Failed creating alert with reason {}", response.getStatusLine().getReasonPhrase());
            }

        } catch (JsonProcessingException e) {
            logger.error("Failed preparing json payload for alert creation", e);
        } catch (IOException e) {
            // done
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }

}

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

package org.openkilda.wfm.topology.ifmon.bolts;

import static org.openkilda.messaging.Utils.MAPPER;
import static org.openkilda.wfm.topology.ifmon.data.InterfaceMetricConst.TAG_PORT;
import static org.openkilda.wfm.topology.ifmon.data.InterfaceMetricConst.TAG_SWITCHID;

import org.openkilda.messaging.info.Datapoint;
import org.openkilda.wfm.topology.ifmon.utils.AlertaAlertPayload;
import org.openkilda.wfm.topology.ifmon.utils.AlertaClient;
import org.openkilda.wfm.topology.ifmon.utils.MicroServiceClient;
import org.openkilda.wfm.topology.ifmon.utils.PisSwitch;
import org.openkilda.wfm.topology.ifmon.utils.Utils;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;



/*
 * tuples received by this bolt signaling potential errors. This bold will try to trigger alerts with proper information
 */
public class InterfaceMetricAlerterBolt extends BaseRichBolt {

    private String tpnEndpoint;
    private String tpnUserName;
    private String tpnPassword;

    private String alertaEndpoint;
    private String alertaApiKey;

    private OutputCollector collector;
    private MicroServiceClient msClient;
    private AlertaClient alertaClient;

    private static Logger logger = LoggerFactory.getLogger(InterfaceMetricAlerterBolt.class);

    public InterfaceMetricAlerterBolt(String tpnEndpoint, String tpnUser, String tpnPassword, 
            String alertaEndpoint, String alertaApiKey) {
        this.tpnEndpoint = tpnEndpoint;
        this.tpnUserName = tpnUser;
        this.tpnPassword = tpnPassword;
        
        this.alertaEndpoint = alertaEndpoint;
        this.alertaApiKey = alertaApiKey;
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
        msClient = new MicroServiceClient(tpnEndpoint, tpnUserName, tpnPassword);
        logger.debug("Successfully created micro service client");
        
        alertaClient = new AlertaClient(alertaEndpoint, alertaApiKey);
        logger.debug("Successfully created Alerta client");

    }

    @Override
    public void execute(Tuple tuple) {

        final String data = tuple.getString(0);

        try {
            Datapoint datapoint = MAPPER.readValue(data, Datapoint.class);
            String metric = datapoint.getMetric();
            long timestamp = datapoint.getTimestamp();
            String switchId = datapoint.getTags().get(TAG_SWITCHID);
            String port = datapoint.getTags().get(TAG_PORT);

            String legacyDpid = Utils.dpidKildaToLegacy(switchId);
            PisSwitch sw = msClient.resolveSwitch(legacyDpid);
            String switchName = sw.getCommonName();

            logger.error("Switch {} port {} metric {} found monotonically increasing", switchName, port, metric);

            // Call PIS to get the switch port type: ISL, Customer, VNF, Exchange, and DIA
            String portDescription = msClient.getSwitchPortDetails(sw, Integer.valueOf(port));
            logger.info("Get {}:{} description from PIS: {}", switchName, port, portDescription);
            

            // Create alerta alert
            AlertaAlertPayload alert = AlertaAlertPayload.builder()
                                       .origin("Kilda")
                                       .environment("PROD")
                                       .resource(String.format("%s:%s", switchName, port))
                                       .event(metric)
                                       .status("Open")
                                       .service(Arrays.asList("Kilda"))
                                       .createTime(timestamp)
                                       .build();
            
            alertaClient.createAlert(alert);
            logger.info("Created alert for {}:{} on {}", switchName, port, metric);
                                      
        } catch (Exception e) {
            logger.error("Failed reading data:" + data, e);
        } finally {
            collector.ack(tuple);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {

    }

}

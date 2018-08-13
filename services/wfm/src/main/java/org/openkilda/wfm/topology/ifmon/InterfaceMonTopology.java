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

package org.openkilda.wfm.topology.ifmon;

import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.error.NameCollisionException;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.ifmon.InterfaceMonTopologyConfig.InterfaceMonConfig;
import org.openkilda.wfm.topology.ifmon.bolts.InterfaceMetricAlerterBolt;
import org.openkilda.wfm.topology.ifmon.bolts.InterfaceMetricFilterBolt;
import org.openkilda.wfm.topology.ifmon.bolts.InterfaceMetricMonitorBolt;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.topology.TopologyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


public class InterfaceMonTopology extends AbstractTopology<InterfaceMonTopologyConfig> {

    private static final Logger logger = LoggerFactory.getLogger(InterfaceMonTopology.class);
    private static final String INTERFACE_MON_TSDB_SPOUT_ID = "ifmon-spout";
    private static final String INTERFACE_MON_FILTER_BOLT = "ifmon-filter-bolt";
    private static final String INTERFACE_MON_MONITOR_BOLT = "ifmon-monitor-bolt";
    private static final String INTERFACE_MON_ALERTER_BOLT = "ifmon-alerter-bolt";

    public InterfaceMonTopology(LaunchEnvironment env) {
        super(env, InterfaceMonTopologyConfig.class);
    }

    @Override
    public StormTopology createTopology() throws NameCollisionException {
        logger.info("Create InterfaceMonTopology - {}", topologyName);

        InterfaceMonConfig config = topologyConfig.getInterfaceMonConfig();

        String kafkaOtsdbTopic = topologyConfig.getKafkaOtsdbTopic();
        checkAndCreateTopic(kafkaOtsdbTopic);

        logger.debug("connecting to {} topic", kafkaOtsdbTopic);
        TopologyBuilder builder = new TopologyBuilder();
        builder.setSpout(INTERFACE_MON_TSDB_SPOUT_ID, createKafkaSpout(kafkaOtsdbTopic, INTERFACE_MON_TSDB_SPOUT_ID),
                config.getNumSpouts());

        // filter tuples based on the metric we are interested in
        List<String> metrics = config.getInterfaceMetrics();
        InterfaceMetricFilterBolt filterBolt = new InterfaceMetricFilterBolt(metrics);
        builder.setBolt(INTERFACE_MON_FILTER_BOLT, filterBolt, config.getNumFilterBoltExecutors())
                .shuffleGrouping(INTERFACE_MON_TSDB_SPOUT_ID);

        // Any interface metric signaling trouble?
        InterfaceMetricMonitorBolt monitorBolt = new InterfaceMetricMonitorBolt(metrics,
                config.getMonitorBoltCacheTimeout(), config.getMonitorBoltWindowsize());
        builder.setBolt(INTERFACE_MON_MONITOR_BOLT, monitorBolt, config.getNumMonitorBoltExecutors())
                .shuffleGrouping(INTERFACE_MON_FILTER_BOLT);

        // Potential problematic interface metrics will trigger alert to both Kabana and
        // Alertera
        InterfaceMetricAlerterBolt alerterBolt = new InterfaceMetricAlerterBolt(
                config.getTpnEndpoint(),
                config.getTpnUsername(), 
                config.getTpnPassword(),
                config.getAlertaEndpoint(),
                config.getAlertApiKey());
        builder.setBolt(INTERFACE_MON_ALERTER_BOLT, alerterBolt, config.getNumAltererBoltExecutors())
                .shuffleGrouping(INTERFACE_MON_MONITOR_BOLT);

        // createHealthCheckHandler(builder, ServiceType.PACKETMON_TOPOLOGY.getId());

        return builder.createTopology();
    }

    /**
     * Main run loop.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new InterfaceMonTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }

}

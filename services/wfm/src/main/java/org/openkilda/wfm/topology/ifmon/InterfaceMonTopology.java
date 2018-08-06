package org.openkilda.wfm.topology.ifmon;

import java.util.List;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.topology.TopologyBuilder;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.error.NameCollisionException;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.ifmon.InterfaceMonTopologyConfig.InterfaceMonConfig;
import org.openkilda.wfm.topology.ifmon.bolts.InterfaceMetricAlerterBolt;
import org.openkilda.wfm.topology.ifmon.bolts.InterfaceMetricFilterBolt;
import org.openkilda.wfm.topology.ifmon.bolts.InterfaceMetricMonitorBolt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterfaceMonTopology extends AbstractTopology<InterfaceMonTopologyConfig> {

	private final static Logger logger = LoggerFactory.getLogger(InterfaceMonTopology.class);
	private final static String INTERFACE_MON_TSDB_SPOUT_ID = "ifmon-spout";
	private final static String INTERFACE_MON_FILTER_BOLT = "ifmon-filter-bolt";
	private final static String INTERFACE_MON_MONITOR_BOLT = "ifmon-monitor-bolt";
	private final static String INTERFACE_MON_ALERTER_BOLT = "ifmon-alerter-bolt";

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
		InterfaceMetricAlerterBolt alerterBolt = new InterfaceMetricAlerterBolt(config.getTpnEndpoint(),
				config.getTpnUsername(), config.getTpnPassword());
		builder.setBolt(INTERFACE_MON_ALERTER_BOLT, alerterBolt, config.getNumAltererBoltExecutors())
				.shuffleGrouping(INTERFACE_MON_MONITOR_BOLT);

		// createHealthCheckHandler(builder, ServiceType.PACKETMON_TOPOLOGY.getId());

		return builder.createTopology();
	}

	/**
	 * Main run loop.
	 *
	 * @param args
	 *            Command line arguments
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

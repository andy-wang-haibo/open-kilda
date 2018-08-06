package org.openkilda.wfm.topology.ifmon.bolts;

import static org.openkilda.messaging.Utils.MAPPER;
import static org.openkilda.wfm.topology.ifmon.data.InterfaceMetricConst.TAG_PORT;
import static org.openkilda.wfm.topology.ifmon.data.InterfaceMetricConst.TAG_SWITCHID;

import java.util.Map;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;
import org.openkilda.messaging.info.Datapoint;
import org.openkilda.wfm.topology.ifmon.utils.MicroServiceClient;
import org.openkilda.wfm.topology.ifmon.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * tuples received by this bolt signaling potential errors. This bold will try to trigger alerts with proper information
 */
public class InterfaceMetricAlerterBolt extends BaseRichBolt {

	private String tpnEndpoint;
	private String tpnUserName;
	private String tpnPassword;
	
	private OutputCollector collector;
	private MicroServiceClient msClient;
	
	private static Logger logger = LoggerFactory.getLogger(InterfaceMetricAlerterBolt.class);
	
	public InterfaceMetricAlerterBolt(String tpnEndpoint, String tpnUser, String tpnPassword) {
		this.tpnEndpoint = tpnEndpoint;
		this.tpnUserName = tpnUser;
		this.tpnPassword = tpnPassword;
	}
	
	@Override
	public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
		this.collector = collector;
		msClient = new MicroServiceClient(tpnEndpoint, tpnUserName, tpnPassword);
		
	}

	@Override
	public void execute(Tuple tuple) {
		
		final String data = tuple.getString(0);
		
		try {
			Datapoint datapoint = MAPPER.readValue(data, Datapoint.class);
			String metric = datapoint.getMetric();
			String switchId = datapoint.getTags().get(TAG_SWITCHID);
			String port = datapoint.getTags().get(TAG_PORT);
			
			String legacyDpid = Utils.dpidKildaToLegacy(switchId);
			String switchName = msClient.resolveSwitch(legacyDpid).getCommonName();
			
			logger.error("Switch {} port {} metric {} found monotonically increasing", switchName, port, metric);
			
		} catch(Exception e) {
			logger.error("Failed reading data:" + data, e);
		} finally {
			collector.ack(tuple);
		}
	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {

	}

}

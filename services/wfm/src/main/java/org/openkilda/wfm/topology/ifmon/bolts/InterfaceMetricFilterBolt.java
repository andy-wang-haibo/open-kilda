package org.openkilda.wfm.topology.ifmon.bolts;

import static org.openkilda.messaging.Utils.MAPPER;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.openkilda.messaging.info.Datapoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * filters data points with specific metric name
 * pen.switch.rx-crc-error
 * pen.switch.collisions
 * pen.switch.rx-over-error
 * pen.switch.rx-dropped
 * pen.switch.tx-dropped
 * pen.switch.rx-errors
 * pen.switch.tx-errors
 * pen.switch.rx-frame-error
 */


public class InterfaceMetricFilterBolt extends BaseRichBolt {

	private final static Logger logger = LoggerFactory.getLogger(InterfaceMetricFilterBolt.class);
	private final List<String> metrics;
	private OutputCollector collector;

	public InterfaceMetricFilterBolt(List<String> metrics) {

		this.metrics = metrics;
	}

	@Override
	public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
		this.collector = collector;
	}

	@Override
	public void execute(Tuple tuple) {
		final String data = tuple.getString(0);
		try {
			Datapoint datapoint = MAPPER.readValue(data, Datapoint.class);

			// the tuple is not anchored, not affecting how spouts ack it.
			if (metrics.contains(datapoint.getMetric())) {
				collector.emit(Arrays.asList(tuple));
			}
		} catch (Exception e) {
			logger.error("Failed reading data:" + data, e);
		} finally {
			collector.ack(tuple);
		}

	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declare(new Fields("datapoint"));
	}

}

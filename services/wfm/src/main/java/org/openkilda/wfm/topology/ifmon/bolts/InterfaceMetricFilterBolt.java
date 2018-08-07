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

import org.openkilda.messaging.info.Datapoint;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;


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

    private static final Logger logger = LoggerFactory.getLogger(InterfaceMetricFilterBolt.class);
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

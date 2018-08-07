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
import org.openkilda.wfm.topology.ifmon.data.InterfaceMetricStats;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

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
import java.util.concurrent.TimeUnit;

public class InterfaceMetricMonitorBolt extends BaseRichBolt {
    
    private final List<String> metrics;
    private final int cacheTimeoutSeconds;
    private final int windowSize;
    
    private Map<String, LoadingCache<String, InterfaceMetricStats>> cache;
    private OutputCollector collector;
    
    private static Logger logger = LoggerFactory.getLogger(InterfaceMetricMonitorBolt.class);
    
    public InterfaceMetricMonitorBolt(List<String> metrics, int cacheTimeoutSeconds, int windowSize) {
        this.metrics = metrics;
        this.cacheTimeoutSeconds = cacheTimeoutSeconds;
        this.windowSize = windowSize;
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
        
        // construct the cache holding interface metrics, which will be used to determine
        // if it signals potential problems
        for (String metric: metrics) {
            LoadingCache<String, InterfaceMetricStats> metricCache = 
                    CacheBuilder.newBuilder()
                    .expireAfterAccess(cacheTimeoutSeconds, TimeUnit.SECONDS)
                    .build(new CacheLoader<String, InterfaceMetricStats>() {
                        @Override
                        public InterfaceMetricStats load(String s) {
                            return new InterfaceMetricStats(windowSize);
                        }
                    });
            
            cache.put(metric, metricCache);        
        }
        
    }

    @Override
    public void execute(Tuple tuple) {
        final String data = tuple.getString(0);
        
        try {
            Datapoint datapoint =  MAPPER.readValue(data, Datapoint.class);
            String metric = datapoint.getMetric();
            String switchId = datapoint.getTags().get(TAG_SWITCHID);
            String port = datapoint.getTags().get(TAG_PORT);
            InterfaceMetricStats ifStats = cache.get(metric).get(String.format("%s_%s", switchId, port));
            
            ifStats.add(datapoint.getTimestamp(), datapoint.getValue().doubleValue());
            if (ifStats.isValueMonotonicallyIncreasing()) {
                // here we clear the the stats to avoid the situation, where if the interface is problematic,
                // each incoming metric will trigger an alert. This is an area need more tweaking
                ifStats.reset();
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

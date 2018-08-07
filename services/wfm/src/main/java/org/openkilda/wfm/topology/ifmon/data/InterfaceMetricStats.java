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

package org.openkilda.wfm.topology.ifmon.data;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

public class InterfaceMetricStats {

    private DescriptiveStatistics dataValues;

    private int windowSize;
    private long lastTimeValue;

    public InterfaceMetricStats(int windowSize) {
        this.windowSize = windowSize;
        this.dataValues = new DescriptiveStatistics(windowSize);
    }

    /**
     * Add a new value to the stats.
     * 
     * @param time in epoc milliseconds associated with the value
     * @param value the metric value
     */
    public void add(long time, double value) {
        /*
         * 
         * past experience shows the datapoint may be out of order with regard to time,
         * and might be duplicated,
         * 
         */
        if (time > lastTimeValue) {            
            lastTimeValue = time;
            dataValues.addValue(value);
        }
    }

    /**
     * Check if values are monotonically increasing: each value is greater than or
     * equal to previous one, and the last value is greater than the first one.
     * 
     * @return true if later datapoint is greater than previous ones, else false.
     */
    public boolean isValueMonotonicallyIncreasing() {
        double[] values = dataValues.getValues();

        if (values.length < windowSize) {
            return false;
        }

        for (int i = 1; i < values.length; i++) {
            if (values[i] < values[i - 1]) {
                return false;
            }
        }
        return values[values.length - 1] > values[0];

    }

    public void reset() {
        dataValues.clear();
    }
}

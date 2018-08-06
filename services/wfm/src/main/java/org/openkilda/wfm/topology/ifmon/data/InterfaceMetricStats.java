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
	
	
	public void add(long time, double value) {
		// past experience shows the datapoint may be out of order with regard to time, and might be duplicated
		// likely for every possible switch_port, the metric comes at regular intervals, like every 10s
		if (time > lastTimeValue ) {
			lastTimeValue = time;
			dataValues.addValue(value);
		}
	}
	
	/**
	 * Check if values are monotonically increasing: 
	 * each value is greater than or equal to previous one, and the last value is greater than the first one
	 * @return true if later datapoint is greater than previous ones, else false.
	 */
	public boolean isValueMonotonicallyIncreasing() {
		double[] values = dataValues.getValues();
		
		if (values.length < windowSize) {
			return false;
		}
		
		for (int i = 1; i < values.length; i++ ) {
			if ( values[i] < values[i - 1 ]) {
				return false;
			}
		}
		return values[values.length - 1] > values[0];
		
	}


	public void reset() {
		dataValues.clear();
	}
}

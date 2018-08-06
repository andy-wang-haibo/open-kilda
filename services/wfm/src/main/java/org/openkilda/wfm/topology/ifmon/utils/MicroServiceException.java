package org.openkilda.wfm.topology.ifmon.utils;

public class MicroServiceException extends Exception {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public MicroServiceException(String message) {
		super(message);
	}
	
	public MicroServiceException(Exception e) {
		super(e);
	}
}

package org.openkilda.wfm.topology.ifmon.utils;

public class Utils {
	
	public static String dpidKildaToLegacy(String dpid) {
		return "SW" + dpid.toUpperCase().replaceAll(":", "");
	}
	
	public static String dpidLegacyToKilda(String dpid) {
		return dpid.toLowerCase().substring("SW".length()).replaceAll("[0-9a-f]{2}(?!$)", "$0:");
	}
}

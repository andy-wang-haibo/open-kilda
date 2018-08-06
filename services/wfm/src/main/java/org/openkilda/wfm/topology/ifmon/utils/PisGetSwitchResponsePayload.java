package org.openkilda.wfm.topology.ifmon.utils;

import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)

public class PisGetSwitchResponsePayload {

	@JsonProperty("switches")
	private List<PisSwitch> switches;

	@JsonCreator
	public PisGetSwitchResponsePayload(@JsonProperty("switches") final List<PisSwitch> switches) {
		this.switches = switches;
	}

	public List<PisSwitch> getSwitches() {
		return switches;
	}

	public void setSwitches(List<PisSwitch> switches) {
		this.switches = switches;
	}

	@Override
	public String toString() {

		List<String> allSwitches = switches.stream().map(x -> x.toString()).collect(Collectors.toList());

		return "PisGetSwitchResponsePayload [switches=" + String.join("\n", allSwitches) + "]";
	}

}

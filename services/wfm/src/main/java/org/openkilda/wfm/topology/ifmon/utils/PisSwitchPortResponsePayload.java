package org.openkilda.wfm.topology.ifmon.utils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(value = { "circuitid", "cablebearerid", "notes"})
@JsonIgnoreProperties({ "assignmenttype", "interfacetype", "status", "crossconnect", "odfmdf", "mmr",
		"remoteswitchcode", "remoteport", "provider", "bandwidth", "latency", "farenddescription", "helpdeskphone",
		"comment", "email", "faulthistory" })
public class PisSwitchPortResponsePayload {

	@JsonProperty("circuitid")
	private String circuitId;
	@JsonProperty("cablebearerid")
	private String cableBearerId;
	@JsonProperty("notes")
	private String notes;

	@JsonCreator
	public PisSwitchPortResponsePayload(@JsonProperty("circuitid") final String circuitId,
			@JsonProperty("cablebearerid") String cableBearerId, @JsonProperty("notes") final String notes) {

		this.circuitId = circuitId;
		this.cableBearerId = cableBearerId;
		this.notes = notes;
	}

	public String getCircuitId() {
		return circuitId;
	}

	public void setCircuitId(String circuitId) {
		this.circuitId = circuitId;
	}

	public String getCableBearerId() {
		return cableBearerId;
	}

	public void setCableBearerId(String cableBearerId) {
		this.cableBearerId = cableBearerId;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((cableBearerId == null) ? 0 : cableBearerId.hashCode());
		result = prime * result + ((circuitId == null) ? 0 : circuitId.hashCode());
		result = prime * result + ((notes == null) ? 0 : notes.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PisSwitchPortResponsePayload other = (PisSwitchPortResponsePayload) obj;
		if (cableBearerId == null) {
			if (other.cableBearerId != null)
				return false;
		} else if (!cableBearerId.equals(other.cableBearerId))
			return false;
		if (circuitId == null) {
			if (other.circuitId != null)
				return false;
		} else if (!circuitId.equals(other.circuitId))
			return false;
		if (notes == null) {
			if (other.notes != null)
				return false;
		} else if (!notes.equals(other.notes))
			return false;
		return true;
	}


	
	
}

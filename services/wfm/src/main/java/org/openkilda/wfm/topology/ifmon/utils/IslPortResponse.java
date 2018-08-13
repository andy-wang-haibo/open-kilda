/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.topology.ifmon.utils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties({"interfacetype", "status", "crossconnect", "odfmdf", "mmr",
        "remoteswitchcode", "remoteport", "provider", "bandwidth", "latency", "farenddescription", "helpdeskphone",
        "comment", "email", "faulthistory" })
@Getter
@Setter
@EqualsAndHashCode
public class IslPortResponse {

    @JsonProperty(value = "assignmenttype", defaultValue = "ISL")
    private String assignmentType;
    
    @JsonProperty("circuitid")
    private String circuitId;
    
    @JsonProperty("cablebearerid")
    private String cableBearerId;
    
    @JsonProperty("notes")
    private String notes;

    @JsonCreator
    public IslPortResponse(
            @JsonProperty("assignmenttype") final String assignmentType,
            @JsonProperty("circuitid") final String circuitId,
            @JsonProperty("cablebearerid") String cableBearerId, 
            @JsonProperty("notes") final String notes) {
        
        this.assignmentType = assignmentType;
        this.circuitId = circuitId;
        this.cableBearerId = cableBearerId;
        this.notes = notes;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder()
                .append(String.format("%s port information:\n", assignmentType))
                .append(String.format("\tCircuit Id: %s\n", circuitId))
                .append(String.format("\tCable Bearer Id: %s\n", cableBearerId))
                .append(String.format("\tNotes: %s\n", notes));

        return builder.toString();
    }
    
}

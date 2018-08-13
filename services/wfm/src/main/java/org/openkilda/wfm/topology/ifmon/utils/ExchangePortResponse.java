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

/**
 * Very simplified class used to extract only relevant information from PIS Exchange
 * port for alert creation purpose.
 * 
 * @author andy
 *
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties({"interfacetype", "status", "crossconnect", "notes", "odfmdf", "mmr"})
@Getter
@Setter
@EqualsAndHashCode
public class ExchangePortResponse {

    @JsonProperty(value = "assignmenttype", defaultValue = "Exchange")
    private String assignmentType;
    
    @JsonProperty("circuitid")
    private String circuitId;
    
    @JsonProperty("provideruuid")
    private String providerUuid;
    
    @JsonProperty("telstrarefid")
    private String telstraRefid;
    
    @JsonCreator
    public ExchangePortResponse(
            @JsonProperty("assignmenttype") final String assignmentType,
            @JsonProperty("circuitid") final String circuitId,
            @JsonProperty("provideruuid") final String providerUuid,
            @JsonProperty("telstrarefid") final String telstraRefid) {
        this.assignmentType = assignmentType;
        this.circuitId = circuitId;
        this.providerUuid = providerUuid;
        this.telstraRefid = telstraRefid;
        
    }
    
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder()
                .append(String.format("%s port information:\n", assignmentType))
                .append(String.format("\tCircuit Id: %s\n", circuitId))
                .append(String.format("\tProvider UUID: %s\n", providerUuid))
                .append(String.format("\tTelstra Refid: %s\n", telstraRefid));
        
        return builder.toString();
    }
}

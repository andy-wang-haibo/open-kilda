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
 * Very simplified class used to extract only relevant information from PIS VNF
 * port for alert creation purpose.
 * 
 * @author andy
 *
 */

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties({ "interfacetype", "status", "crossconnect", "odfmdf", "notes", "mmr", "vlanrange" })
@Getter
@Setter
@EqualsAndHashCode
public final class VnfPortResponse {

    @JsonProperty(value = "assignmenttype", defaultValue = "VNF")
    private String assignmentType;

    @JsonProperty("hostname")
    private String hostName;

    @JsonCreator
    public VnfPortResponse(
            @JsonProperty("assignmenttype") final String assignmentType,
            @JsonProperty("hostname") final String hostName) {

        this.assignmentType = assignmentType;
        this.hostName = hostName;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder()
                .append(String.format("%s port information:\n", assignmentType))
                .append(String.format("\tHost Name: %s\n", hostName));

        return builder.toString();
    }

}

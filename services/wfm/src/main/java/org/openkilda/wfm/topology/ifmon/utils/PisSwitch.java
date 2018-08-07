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

package org.openkilda.wfm.topology.ifmon.utils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(value = { "popuuid", "dpid", "commonname", "uuid" })
public class PisSwitch {

    @JsonProperty("popuuid")
    private String popUuid;

    @JsonProperty("dpid")
    private String dpid;

    @JsonProperty("commonname")
    private String commonName;

    @JsonProperty("uuid")
    private String uuid;

    @JsonCreator
    public PisSwitch(@JsonProperty("popuuid") final String popUuid, @JsonProperty("dpid") final String dpid,
            @JsonProperty("commonname") final String commonName, @JsonProperty("uuid") final String uuid) {

        this.popUuid = popUuid;
        this.dpid = dpid;
        this.commonName = commonName;
        this.uuid = uuid;
    }

    public String getPopUuid() {
        return popUuid;
    }

    public void setPopUuid(String popUuid) {
        this.popUuid = popUuid;
    }

    public String getDpid() {
        return dpid;
    }

    public void setDpid(String dpid) {
        this.dpid = dpid;
    }

    public String getCommonName() {
        return commonName;
    }

    public void setCommonName(String commonName) {
        this.commonName = commonName;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public String toString() {
        return "PisSwitch [popUuid=" + popUuid + ", dpid=" + dpid + ", commonName=" + commonName + ", uuid=" + uuid
                + "]";
    }

}

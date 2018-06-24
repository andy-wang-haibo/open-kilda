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

package org.openkilda.messaging.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

/**
 * The aim of this object is to define fuzzy definition of network endpoint. If field is not defined(equal to null),
 * this filed must be treated as having "any possible value".
 */
@Value
public class NetworkEndpointMask extends AbstractNetworkEndpoint {

    @JsonCreator
    public NetworkEndpointMask(
            @JsonProperty("switch-id") String datapath,
            @JsonProperty("port-id") Integer portNumber) {
        super(StringUtils.isEmpty(datapath) ? null : datapath, portNumber);

        if (datapath != null) {
            validateDatapath();
        }

        if (portNumber != null) {
            validatePortNumber();
        }
    }

}

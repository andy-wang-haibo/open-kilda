/* Copyright 2020 Telstra Open Source
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

package org.openkilda.snmp.collector.collection.data;

import static com.google.common.base.MoreObjects.toStringHelper;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Value;

@Value
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SnmpMetricEntry {

    // this is the metric name
    @JsonProperty("metric")
    String metric;

    @JsonProperty("oid")
    String oid;

    @JsonCreator
    public SnmpMetricEntry(@JsonProperty("metric") String metric,
                           @JsonProperty("oid") String oid) {
        this.metric = metric;
        this.oid = oid;
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("alias", metric)
                .add("oid", oid)
                .toString();
    }
}

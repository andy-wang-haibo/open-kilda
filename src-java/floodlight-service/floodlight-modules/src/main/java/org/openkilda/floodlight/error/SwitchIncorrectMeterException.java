/* Copyright 2019 Telstra Open Source
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

package org.openkilda.floodlight.error;

import org.openkilda.model.MeterConfig;
import org.openkilda.model.of.MeterSchema;

import lombok.Getter;
import org.projectfloodlight.openflow.types.DatapathId;

@Getter
public class SwitchIncorrectMeterException extends SwitchOperationException {
    private final MeterConfig meterConfig;
    private final MeterSchema expectedSchema;
    private final MeterSchema actualSchema;

    public SwitchIncorrectMeterException(
            DatapathId dpId, MeterConfig meterConfig, MeterSchema expected, MeterSchema actual,
            boolean isInaccurate) {
        super(dpId, formatMessage(dpId, meterConfig, expected, actual, isInaccurate));
        this.meterConfig = meterConfig;
        this.expectedSchema = expected;
        this.actualSchema = actual;
    }

    private static String formatMessage(
            DatapathId dpId, MeterConfig config, MeterSchema expected, MeterSchema actual, boolean isInaccurate) {
        return String.format(
                "Meter %d on %s have incorrect config - actual value is %s while expected value is %s "
                        + "(is accurate %s, config %s)",
                expected.getMeterId().getValue(), dpId, actual, expected, !isInaccurate, config);
    }
}

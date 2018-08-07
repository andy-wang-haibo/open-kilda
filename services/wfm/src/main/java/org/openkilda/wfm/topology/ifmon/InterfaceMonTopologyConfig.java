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

package org.openkilda.wfm.topology.ifmon;

import org.openkilda.wfm.topology.AbstractTopologyConfig;

import com.sabre.oss.conf4j.annotation.Configuration;
import com.sabre.oss.conf4j.annotation.Default;
import com.sabre.oss.conf4j.annotation.IgnoreKey;
import com.sabre.oss.conf4j.annotation.Key;

import java.util.List;

public interface InterfaceMonTopologyConfig extends AbstractTopologyConfig {

    @IgnoreKey
    InterfaceMonConfig getInterfaceMonConfig();

    default String getKafkaOtsdbTopic() {
        return getKafkaTopics().getOtsdbTopic();
    }

    @Configuration
    @Key("interfacemon")
    interface InterfaceMonConfig {

        @Key("num.spouts")
        @Default("1")
        int getNumSpouts();

        @Key("filterbolt.executors")
        @Default("1")
        int getNumFilterBoltExecutors();

        @Key("filterbolt.tasks")
        @Default("1")
        int getNumFilterBoltTasks();

        @Key("monitorbolt.executors")
        @Default("1")
        int getNumMonitorBoltExecutors();

        @Key("monitorbolt.tasks")
        @Default("1")
        int getNumMonitorBoltTasks();

        @Key("monitorbolt.windowsize")
        @Default("60")
        int getMonitorBoltWindowsize();

        @Key("monitorbolt.cachetimeout.seconds")
        @Default("900")
        int getMonitorBoltCacheTimeout();

        @Key("alerterbolt.executors")
        @Default("1")
        int getNumAltererBoltExecutors();

        @Key("alerterbolt.tasks")
        @Default("1")
        int getNumAlerterBoltTasks();

        @Key("metrics")
        @Default("[pen.switch.rx-errors,pen.switch.tx-errors," + "pen.switch.rx-dropped,pen.switch.tx-dropped,"
                + "pen.switch.rx-crc-error,pen.switch.collisions,"
                + "pen.switch.rx-frame-error,pen.switch.rx-over-error]")
        List<String> getInterfaceMetrics();

        @Key("tpn.endpoint")
        String getTpnEndpoint();

        @Key("tpn.username")
        String getTpnUsername();

        @Key("tpn.password")
        String getTpnPassword();

    }

}

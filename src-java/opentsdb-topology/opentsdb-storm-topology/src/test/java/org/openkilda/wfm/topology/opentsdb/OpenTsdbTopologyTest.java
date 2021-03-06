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

package org.openkilda.wfm.topology.opentsdb;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;

import org.openkilda.messaging.info.Datapoint;
import org.openkilda.wfm.StableAbstractStormTest;

import org.apache.storm.Testing;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.testing.MockedSources;
import org.apache.storm.tuple.Values;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.verify.VerificationTimes;

import java.util.Collections;
import java.util.Map;

public class OpenTsdbTopologyTest extends StableAbstractStormTest {
    private static final long timestamp = System.currentTimeMillis();
    private static ClientAndServer mockServer;
    private static final HttpRequest REQUEST = HttpRequest.request().withMethod("POST").withPath("/api/put");

    @BeforeClass
    public static void setupOnce() throws Exception {
        StableAbstractStormTest.startCompleteTopology();

        mockServer = startClientAndServer(4242);
    }

    @Before
    public void init() {
        mockServer.when(REQUEST)
                .respond(HttpResponse.response().withStatusCode(200));
    }

    @After
    public void cleanup() {
        mockServer.reset();
    }

    @Test
    public void shouldSuccessfulSendDatapoint() {
        Datapoint datapoint = new Datapoint("metric", timestamp, Collections.emptyMap(), 123);

        MockedSources sources = new MockedSources();
        Testing.withTrackedCluster(clusterParam, (cluster) -> {
            OpenTsdbTopology topology = new OpenTsdbTopology(makeLaunchEnvironment());

            sources.addMockData(OpenTsdbTopology.OTSDB_SPOUT_ID,
                    new Values(null, datapoint));
            completeTopologyParam.setMockedSources(sources);

            StormTopology stormTopology = topology.createTopology();

            Map result = Testing.completeTopology(cluster, stormTopology, completeTopologyParam);
        });

        //verify that request is sent to OpenTSDB server
        mockServer.verify(REQUEST, VerificationTimes.exactly(1));
    }

    @Test
    public void shouldSendDatapointRequestsOnlyOnce() throws Exception {
        Datapoint datapoint = new Datapoint("metric", timestamp, Collections.emptyMap(), 123);

        MockedSources sources = new MockedSources();

        Testing.withTrackedCluster(clusterParam, (cluster) -> {
            OpenTsdbTopology topology = new OpenTsdbTopology(makeLaunchEnvironment());

            sources.addMockData(OpenTsdbTopology.OTSDB_SPOUT_ID,
                    new Values(null, datapoint), new Values(null, datapoint));
            completeTopologyParam.setMockedSources(sources);

            StormTopology stormTopology = topology.createTopology();

            Testing.completeTopology(cluster, stormTopology, completeTopologyParam);
        });
        //verify that request is sent to OpenTSDB server once
        mockServer.verify(REQUEST, VerificationTimes.exactly(1));
    }

    @Test
    public void shouldSendDatapointRequestsTwice() throws Exception {
        Datapoint datapoint1 = new Datapoint("metric", timestamp, Collections.emptyMap(), 123);
        Datapoint datapoint2 = new Datapoint("metric", timestamp, Collections.emptyMap(), 456);

        MockedSources sources = new MockedSources();

        Testing.withTrackedCluster(clusterParam, (cluster) -> {
            OpenTsdbTopology topology = new OpenTsdbTopology(makeLaunchEnvironment());

            sources.addMockData(OpenTsdbTopology.OTSDB_SPOUT_ID,
                    new Values(null, datapoint1), new Values(null, datapoint2));
            completeTopologyParam.setMockedSources(sources);

            StormTopology stormTopology = topology.createTopology();

            Testing.completeTopology(cluster, stormTopology, completeTopologyParam);
        });
        //verify that request is sent to OpenTSDB server once
        mockServer.verify(REQUEST, VerificationTimes.exactly(2));
    }
}

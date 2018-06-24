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

package org.openkilda.northbound.service;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.ChunkedInfoMessage;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.model.LinkProps;
import org.openkilda.messaging.model.LinkPropsMask;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.messaging.model.NetworkEndpointMask;
import org.openkilda.messaging.nbtopology.response.LinkPropsData;
import org.openkilda.messaging.te.request.LinkPropsDrop;
import org.openkilda.messaging.te.request.LinkPropsPut;
import org.openkilda.messaging.te.request.LinkPropsRequest;
import org.openkilda.messaging.te.response.LinkPropsResponse;
import org.openkilda.northbound.MessageExchanger;
import org.openkilda.northbound.config.KafkaConfig;
import org.openkilda.northbound.dto.LinkPropsDto;
import org.openkilda.northbound.messaging.MessageConsumer;
import org.openkilda.northbound.messaging.MessageProducer;
import org.openkilda.northbound.service.impl.LinkServiceImpl;
import org.openkilda.northbound.utils.CorrelationIdFactory;
import org.openkilda.northbound.utils.RequestCorrelationId;
import org.openkilda.northbound.utils.TestCorrelationIdFactory;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

@RunWith(SpringRunner.class)
public class LinkServiceTest {
    private int requestIdIndex = 0;

    @Autowired
    private CorrelationIdFactory idFactory;

    @Autowired
    private LinkService linkService;

    @Autowired
    private MessageExchanger messageExchanger;

    @Before
    public void reset() {
        messageExchanger.resetMockedResponses();

        String lastRequestId = idFactory.produceChained("dummy");
        lastRequestId = lastRequestId.substring(0, lastRequestId.indexOf(':')).trim();
        requestIdIndex = Integer.valueOf(lastRequestId) + 1;
    }

    @Test
    public void shouldGetEmptyPropsList() {
        final String correlationId = "empty-link-props";
        Message message = new ChunkedInfoMessage(null, 0, correlationId, null);
        messageExchanger.mockResponse(message);
        RequestCorrelationId.create(correlationId);

        List<LinkPropsDto> result = linkService.getLinkProps(null, 0, null, 0);
        assertTrue("List of link props should be empty", result.isEmpty());
    }

    @Test
    public void shouldGetPropsList() {
        final String correlationId = "non-empty-link-props";

        LinkProps linkProps = new LinkProps(new NetworkEndpoint("00:00:00:00:00:00:00:01", 1),
                new NetworkEndpoint("00:00:00:00:00:00:00:02", 2),
                Collections.singletonMap("cost", "2"));
        LinkPropsData linkPropsData = new LinkPropsData(linkProps);
        Message message = new ChunkedInfoMessage(linkPropsData, 0, correlationId, null);
        messageExchanger.mockResponse(message);
        RequestCorrelationId.create(correlationId);

        List<LinkPropsDto> result = linkService.getLinkProps(null, 0, null, 0);
        assertFalse("List of link props should be empty", result.isEmpty());

        LinkPropsDto dto = result.get(0);
        assertThat(dto.getSrcSwitch(), is(linkPropsData.getLinkProps().getSource().getDatapath()));
        assertThat(dto.getSrcPort(), is(linkPropsData.getLinkProps().getSource().getPortNumber()));
        assertThat(dto.getDstSwitch(), is(linkPropsData.getLinkProps().getDest().getDatapath()));
        assertThat(dto.getDstPort(), is(linkPropsData.getLinkProps().getDest().getPortNumber()));
    }

    @Test
    public void putLinkProps() throws Exception {
        final String correlationId = getClass().getCanonicalName();

        HashMap<String, String> requestProps = new HashMap<>();
        requestProps.put("test", "value");
        LinkProps linkProps = new LinkProps(
                new NetworkEndpoint("ff:fe:00:00:00:00:00:01", 8),
                new NetworkEndpoint("ff:fe:00:00:00:00:00:02", 9),
                requestProps);
        LinkPropsRequest request = new LinkPropsPut(linkProps);

        LinkPropsResponse payload = new LinkPropsResponse(request, linkProps, null);
        String subCorrelationId = idFactory.produceChained(String.valueOf(requestIdIndex++), correlationId);
        messageExchanger.mockResponse(new InfoMessage(payload, System.currentTimeMillis(), subCorrelationId));

        LinkPropsDto inputItem = new LinkPropsDto(
                linkProps.getSource().getDatapath(), linkProps.getSource().getPortNumber(),
                linkProps.getDest().getDatapath(), linkProps.getDest().getPortNumber(),
                requestProps);

        RequestCorrelationId.create(correlationId);
        LinkPropsResult result = linkService.setLinkProps(Collections.singletonList(inputItem));

        assertThat(result.getFailures(), is(0));
        assertThat(result.getSuccesses(), is(1));
        assertThat(result.getMessages().length, is(0));
    }

    @Test
    public void dropLinkProps() throws Exception {
        final String correlationId = getClass().getCanonicalName();

        LinkPropsDto input = new LinkPropsDto(
                "ff:fe:00:00:00:00:00:01", 8, null, null, null);

        LinkPropsDrop request = new LinkPropsDrop(new LinkPropsMask(
                new NetworkEndpointMask(input.getSrcSwitch(), input.getSrcPort()),
                new NetworkEndpointMask(input.getDstSwitch(), input.getDstPort())));

        LinkProps linkProps = new LinkProps(
                new NetworkEndpoint(input.getSrcSwitch(), input.getSrcPort()),
                new NetworkEndpoint("ff:fe:00:00:00:00:00:02", 9),
                new HashMap<>());

        LinkPropsResponse payload = new LinkPropsResponse(request, linkProps, null);

        String[] requestIdBatch = new String[] {
                idFactory.produceChained(String.valueOf(requestIdIndex++), correlationId),
                idFactory.produceChained(String.valueOf(requestIdIndex++), correlationId)};
        messageExchanger.mockResponse(new ChunkedInfoMessage(
                payload, System.currentTimeMillis(), requestIdBatch[0], requestIdBatch[1]));
        messageExchanger.mockResponse(new ChunkedInfoMessage(
                null, System.currentTimeMillis(), requestIdBatch[1], null));

        RequestCorrelationId.create(correlationId);
        LinkPropsResult result = linkService.delLinkProps(Collections.singletonList(input));

        assertThat(result.getFailures(), is(0));
        assertThat(result.getSuccesses(), is(1));
        assertThat(result.getMessages().length, is(0));
    }

    @TestConfiguration
    @Import(KafkaConfig.class)
    @ComponentScan({
            "org.openkilda.northbound.converter",
            "org.openkilda.northbound.utils"})
    @PropertySource({"classpath:northbound.properties"})
    static class Config {
        @Bean
        public CorrelationIdFactory idFactory() {
            return new TestCorrelationIdFactory();
        }

        @Bean
        public MessageExchanger messageExchanger() {
            return new MessageExchanger();
        }

        @Bean
        public MessageConsumer messageConsumer(MessageExchanger messageExchanger) {
            return messageExchanger;
        }

        @Bean
        public MessageProducer messageProducer(MessageExchanger messageExchanger) {
            return messageExchanger;
        }

        @Bean
        public RestTemplate restTemplate() {
            return mock(RestTemplate.class);
        }

        @Bean
        public LinkService linkService() {
            return new LinkServiceImpl();
        }
    }
}

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
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Payload class for create an Alerta Alert.
 * 
 * @see <pre>http://alerta.readthedocs.io/en/latest/api/reference.html</pre>
 * 
 * @author andy
 *
 */
@JsonSerialize
@JsonInclude(value = JsonInclude.Include.NON_NULL)
@Getter
@Setter
public class AlertaAlertPayload {
    
    private String resource;
    
    private String event;
    
    private String environment;
    
    private String severity;
    
    private List<String> correlate;
    
    private String status;
    
    private List<String> service;
    
    private String group;
    
    private String value;
    
    private String text;
    
    private Set<String> tags;
    
    private Map<String, String> attributes;
    
    private String origin;
    
    private String type;
    
    @JsonSerialize(using = CustomDateSerializer.class)
    private long createTime;
    
    @JsonInclude(value = JsonInclude.Include.NON_DEFAULT)
    private int timeout;
    
    private String rawData;

    
    @JsonCreator
    @Builder
    public AlertaAlertPayload(String resource, 
                            String event, 
                            String environment, 
                            String severity, 
                            List<String> correlate,
                            String status, 
                            List<String> service, 
                            String group, 
                            String value, 
                            String text, 
                            Set<String> tags,
                            Map<String, String> attributes, 
                            String origin, 
                            String type, 
                            long createTime, 
                            int timeout, 
                            String rawData) {
        super();
        this.resource = resource;
        this.event = event;
        this.environment = environment;
        this.severity = severity;
        this.correlate = correlate;
        this.status = status;
        this.service = service;
        this.group = group;
        this.value = value;
        this.text = text;
        this.tags = tags;
        this.attributes = attributes;
        this.origin = origin;
        this.type = type;
        this.createTime = createTime;
        this.timeout = timeout;
        this.rawData = rawData;
    }
    
    
    /**
     * Custom serializer to convert a epoch milliseconds to ISO-8601 formatted string.
     * 
     * @author andy
     *
     */
    static class CustomDateSerializer extends StdSerializer<Long> {
        
        public CustomDateSerializer() {
            this(null);
        }

        public CustomDateSerializer(Class<Long> t) {
            super(t);
            
        }

        @Override
        public void serialize(Long timestamp, JsonGenerator gen, SerializerProvider provider) throws IOException {
            ZonedDateTime utcTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of("UTC"));
            gen.writeString(utcTime.format(DateTimeFormatter.ISO_INSTANT));
            
        }
    }
}

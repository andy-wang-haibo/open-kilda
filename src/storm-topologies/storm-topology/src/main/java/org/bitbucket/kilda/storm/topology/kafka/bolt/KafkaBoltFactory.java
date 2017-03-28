package org.bitbucket.kilda.storm.topology.kafka.bolt;

import java.util.Properties;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.bitbucket.kilda.storm.topology.kafka.bolt.selector.DefaultTopicSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaBoltFactory {

	private static final Logger logger = LoggerFactory.getLogger(KafkaBoltFactory.class);
	
	private final String host;

	private final Integer port;

	private final String keySerializer;

	private final String valueSerializer;
	
	private final Integer acks;
	
	@Inject
	public KafkaBoltFactory(@Named("kafka.host") String host, @Named("kafka.port") Integer port,
			@Named("kafka.bolt.keySerializer") String keySerializer, @Named("kafka.bolt.valueSerializer") String valueSerializer,
			@Named("kafka.bolt.acks") Integer acks) {
		this.host = host;
		this.port = port;
		this.keySerializer = keySerializer;
		this.valueSerializer = valueSerializer;
		this.acks = acks;
	}
	
	public KafkaBolt create(String topic) {
		Properties producerProperties = new Properties();
		producerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers());
		producerProperties.put("acks", acks);
		producerProperties.put("key.serializer", keySerializer); // TODO - for key, use constant when new library becomes available
		producerProperties.put("value.serializer", valueSerializer); // TODO - for key, use constant when new library becomes available
        
		return new KafkaBolt()
				.withProducerProperties(producerProperties)
				.withTopicSelector(new DefaultTopicSelector(topic))
				.withTupleToKafkaMapper(null);
	}
	
	public String getBootstrapServers() {
		return host + ":" + port;
	}
}

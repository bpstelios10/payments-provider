package org.learnings.payments.transactionsservice.adapters.messaging.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "kafka")
public class KafkaProperties {
    String bootstrapServers;
    String groupId;
}

package com.eolma.auction.adapter.out.kafka;

import com.eolma.auction.application.port.out.EventPublisher;
import com.eolma.common.event.DomainEvent;
import com.eolma.common.kafka.EolmaKafkaProducer;
import org.springframework.stereotype.Component;

@Component
public class KafkaEventPublisher implements EventPublisher {

    private static final String TOPIC = "eolma.auction.events";
    private final EolmaKafkaProducer kafkaProducer;

    public KafkaEventPublisher(EolmaKafkaProducer kafkaProducer) {
        this.kafkaProducer = kafkaProducer;
    }

    @Override
    public void publish(DomainEvent<?> event) {
        kafkaProducer.publish(TOPIC, event);
    }
}

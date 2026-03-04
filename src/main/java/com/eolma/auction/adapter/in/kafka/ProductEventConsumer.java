package com.eolma.auction.adapter.in.kafka;

import com.eolma.auction.application.usecase.CreateAuctionUseCase;
import com.eolma.common.event.DomainEvent;
import com.eolma.common.event.EventType;
import com.eolma.common.event.payload.ProductActivatedEvent;
import com.eolma.common.idempotency.IdempotencyChecker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class ProductEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProductEventConsumer.class);
    private final CreateAuctionUseCase createAuctionUseCase;
    private final IdempotencyChecker idempotencyChecker;
    private final ObjectMapper objectMapper;

    public ProductEventConsumer(CreateAuctionUseCase createAuctionUseCase,
                                 IdempotencyChecker idempotencyChecker,
                                 ObjectMapper objectMapper) {
        this.createAuctionUseCase = createAuctionUseCase;
        this.idempotencyChecker = idempotencyChecker;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "eolma.product.events",
            groupId = "eolma-auction",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(DomainEvent<?> event, Acknowledgment ack) {
        log.info("Received event: type={}, eventId={}", event.type(), event.id());

        try {
            if (EventType.PRODUCT_ACTIVATED.equals(event.type())) {
                idempotencyChecker.processOnce(event.id(), () -> {
                    ProductActivatedEvent payload = objectMapper.convertValue(
                            event.payload(), ProductActivatedEvent.class);
                    createAuctionUseCase.execute(payload).block();
                });
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process event: type={}, eventId={}", event.type(), event.id(), e);
        }
    }
}

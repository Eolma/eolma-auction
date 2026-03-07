package com.eolma.auction.adapter.in.kafka;

import com.eolma.auction.application.usecase.CompleteInstantBuyUseCase;
import com.eolma.common.event.DomainEvent;
import com.eolma.common.event.EventType;
import com.eolma.common.event.payload.PaymentConfirmedEvent;
import com.eolma.common.idempotency.IdempotencyChecker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);
    private final CompleteInstantBuyUseCase completeInstantBuyUseCase;
    private final IdempotencyChecker idempotencyChecker;
    private final ObjectMapper objectMapper;

    public PaymentEventConsumer(CompleteInstantBuyUseCase completeInstantBuyUseCase,
                                 IdempotencyChecker idempotencyChecker,
                                 ObjectMapper objectMapper) {
        this.completeInstantBuyUseCase = completeInstantBuyUseCase;
        this.idempotencyChecker = idempotencyChecker;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "eolma.payment.events",
            groupId = "eolma-auction",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(DomainEvent<?> event, Acknowledgment ack) {
        log.info("Received payment event: type={}, eventId={}", event.type(), event.id());

        try {
            if (EventType.PAYMENT_CONFIRMED.equals(event.type())) {
                idempotencyChecker.processOnce(event.id(), () -> {
                    PaymentConfirmedEvent payload = objectMapper.convertValue(
                            event.payload(), PaymentConfirmedEvent.class);
                    completeInstantBuyUseCase.execute(
                            payload.auctionId(), payload.buyerId(), payload.amount()
                    ).block();
                });
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process payment event: type={}, eventId={}", event.type(), event.id(), e);
        }
    }
}

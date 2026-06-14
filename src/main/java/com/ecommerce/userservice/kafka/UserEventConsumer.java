package com.ecommerce.userservice.kafka;

import com.ecommerce.userservice.event.UserRegisteredEvent;
import com.ecommerce.userservice.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventConsumer {

    private final UserProfileService userProfileService;

    /**
     * Consumes user.registered events published by Auth Service.
     *
     * Happy Path (from architecture doc):
     *   Auth Service (producer) → user.registered topic
     *   → User Service (consumer) → create/save user profile
     *   → Notification Service (consumer) → send welcome email
     *
     * This listener handles the User Service side of that chain.
     */
    @KafkaListener(
            topics = "user.registered",
            groupId = "user-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleUserRegistered(
            @Payload UserRegisteredEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("Received [{}] from topic={} partition={} offset={} | userId={} email={}",
                event.getClass().getSimpleName(), topic, partition, offset,
                event.getUserId(), event.getEmail());

        try {
            userProfileService.createProfileFromEvent(event);
            log.info("Successfully created profile for userId={}", event.getUserId());
        } catch (Exception ex) {
            // Log and don't rethrow — prevents poison-pill from blocking the partition.
            // In production, wire up a Dead Letter Topic (DLT) here.
            log.error("Failed to process user.registered for userId={}: {}",
                    event.getUserId(), ex.getMessage(), ex);
        }
    }
}

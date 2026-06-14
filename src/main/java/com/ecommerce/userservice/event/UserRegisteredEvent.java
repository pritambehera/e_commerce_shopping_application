package com.ecommerce.userservice.event;

import lombok.*;

/**
 * Consumed from topic: user.registered  (produced by Auth Service)
 * User Service listens to this event to create the user profile in its own DB.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisteredEvent {
    private String userId;   // Keycloak ID
    private String name;
    private String email;
}

package com.ecommerce.userservice.dto;

import com.ecommerce.userservice.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

// ── Request DTOs ──────────────────────────────────────────────────────────

/**
 * Used by Auth Service (via Kafka user.registered event) to create profile.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class CreateUserProfileRequest {
    @NotBlank
    private String keycloakId;
    @NotBlank @Email
    private String email;
    private String firstName;
    private String lastName;
    @NotNull
    private UserRole role;
}

// ── Exposed as a top-level class ──────────────────────────────────────────

// UpdateProfileRequest.java

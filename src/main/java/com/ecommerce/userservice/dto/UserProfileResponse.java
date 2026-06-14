package com.ecommerce.userservice.dto;

import com.ecommerce.userservice.entity.UserRole;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private Long id;
    private String keycloakId;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String profilePictureUrl;
    private UserRole role;
    private Boolean isActive;
    private List<AddressResponse> addresses;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

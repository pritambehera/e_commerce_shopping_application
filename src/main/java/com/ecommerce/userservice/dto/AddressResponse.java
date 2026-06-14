package com.ecommerce.userservice.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressResponse {
    private Long id;
    private String addressLabel;
    private String fullName;
    private String phone;
    private String streetAddress;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    private Boolean isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

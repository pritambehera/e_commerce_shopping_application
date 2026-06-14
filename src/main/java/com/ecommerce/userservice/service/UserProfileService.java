package com.ecommerce.userservice.service;

import com.ecommerce.userservice.dto.*;
import com.ecommerce.userservice.entity.Address;
import com.ecommerce.userservice.entity.UserProfile;
import com.ecommerce.userservice.entity.UserRole;
import com.ecommerce.userservice.event.UserRegisteredEvent;
import com.ecommerce.userservice.exception.AddressNotFoundException;
import com.ecommerce.userservice.exception.UserProfileNotFoundException;
import com.ecommerce.userservice.repository.AddressRepository;
import com.ecommerce.userservice.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final AddressRepository addressRepository;

    // ── Called by Kafka consumer when user.registered event arrives ──────────

    /**
     * Creates a profile when Auth Service publishes a user.registered event.
     * This is the "Happy Path" action for User Service in the Full Event Chain.
     */
    public void createProfileFromEvent(UserRegisteredEvent event) {
        if (userProfileRepository.existsByKeycloakId(event.getUserId())) {
            log.warn("Profile already exists for keycloakId={}. Skipping.", event.getUserId());
            return;
        }

        // Parse name from event (name field could be "firstName lastName")
        String[] nameParts = event.getName() != null ? event.getName().split(" ", 2) : new String[]{"", ""};
        String firstName = nameParts.length > 0 ? nameParts[0] : "";
        String lastName = nameParts.length > 1 ? nameParts[1] : "";

        UserProfile profile = UserProfile.builder()
                .keycloakId(event.getUserId())
                .email(event.getEmail())
                .firstName(firstName)
                .lastName(lastName)
                .role(UserRole.CUSTOMER) // Default role; Auth Service assigns role in Keycloak
                .isActive(true)
                .build();

        userProfileRepository.save(profile);
        log.info("Created profile for keycloakId={}, email={}", event.getUserId(), event.getEmail());
    }

    // ── Profile CRUD ─────────────────────────────────────────────────────────

    /**
     * Get profile by Keycloak ID (the 'sub' claim from the JWT).
     * Called when a user requests their own profile via GET /api/users/me.
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getProfileByKeycloakId(String keycloakId) {
        UserProfile profile = userProfileRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new UserProfileNotFoundException(
                        "User profile not found for keycloakId: " + keycloakId));
        return mapToResponse(profile);
    }

    /**
     * Get profile by internal DB ID.
     * Used by Admin or internal service-to-service calls.
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getProfileById(Long id) {
        UserProfile profile = userProfileRepository.findById(id)
                .orElseThrow(() -> new UserProfileNotFoundException("User profile not found for id: " + id));
        return mapToResponse(profile);
    }

    /**
     * Update profile fields (name, phone, avatar).
     * Only the profile owner (keycloakId match) can update their own profile.
     */
    public UserProfileResponse updateProfile(String keycloakId, UpdateProfileRequest request) {
        UserProfile profile = userProfileRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new UserProfileNotFoundException(
                        "User profile not found for keycloakId: " + keycloakId));

        if (request.getFirstName() != null) profile.setFirstName(request.getFirstName());
        if (request.getLastName() != null) profile.setLastName(request.getLastName());
        if (request.getPhoneNumber() != null) profile.setPhoneNumber(request.getPhoneNumber());
        if (request.getProfilePictureUrl() != null) profile.setProfilePictureUrl(request.getProfilePictureUrl());

        UserProfile saved = userProfileRepository.save(profile);
        log.info("Updated profile for keycloakId={}", keycloakId);
        return mapToResponse(saved);
    }

    /**
     * Admin: get all user profiles.
     */
    @Transactional(readOnly = true)
    public List<UserProfileResponse> getAllProfiles() {
        return userProfileRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Admin: suspend or activate a user account.
     */
    public UserProfileResponse setUserActiveStatus(Long id, boolean isActive) {
        UserProfile profile = userProfileRepository.findById(id)
                .orElseThrow(() -> new UserProfileNotFoundException("User profile not found for id: " + id));
        profile.setIsActive(isActive);
        UserProfile saved = userProfileRepository.save(profile);
        log.info("Set isActive={} for userId={}", isActive, id);
        return mapToResponse(saved);
    }

    // ── Address Management ────────────────────────────────────────────────────

    /**
     * Get all addresses of the logged-in user.
     */
    @Transactional(readOnly = true)
    public List<AddressResponse> getAddresses(String keycloakId) {
        UserProfile profile = findProfileByKeycloakId(keycloakId);
        return addressRepository.findByUserProfileId(profile.getId()).stream()
                .map(this::mapAddressToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Add a new address for the user.
     */
    public AddressResponse addAddress(String keycloakId, AddressRequest request) {
        UserProfile profile = findProfileByKeycloakId(keycloakId);

        // If this new address is marked as default, clear existing default
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            addressRepository.clearDefaultAddress(profile.getId());
        }

        Address address = Address.builder()
                .userProfile(profile)
                .addressLabel(request.getAddressLabel())
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .streetAddress(request.getStreetAddress())
                .city(request.getCity())
                .state(request.getState())
                .postalCode(request.getPostalCode())
                .country(request.getCountry() != null ? request.getCountry() : "India")
                .isDefault(request.getIsDefault() != null ? request.getIsDefault() : false)
                .build();

        Address saved = addressRepository.save(address);
        log.info("Added address id={} for keycloakId={}", saved.getId(), keycloakId);
        return mapAddressToResponse(saved);
    }

    /**
     * Update an existing address.
     */
    public AddressResponse updateAddress(String keycloakId, Long addressId, AddressRequest request) {
        UserProfile profile = findProfileByKeycloakId(keycloakId);

        Address address = addressRepository.findByIdAndUserProfileId(addressId, profile.getId())
                .orElseThrow(() -> new AddressNotFoundException(
                        "Address not found: id=" + addressId));

        if (Boolean.TRUE.equals(request.getIsDefault())) {
            addressRepository.clearDefaultAddress(profile.getId());
        }

        if (request.getAddressLabel() != null) address.setAddressLabel(request.getAddressLabel());
        if (request.getFullName() != null) address.setFullName(request.getFullName());
        if (request.getPhone() != null) address.setPhone(request.getPhone());
        if (request.getStreetAddress() != null) address.setStreetAddress(request.getStreetAddress());
        if (request.getCity() != null) address.setCity(request.getCity());
        if (request.getState() != null) address.setState(request.getState());
        if (request.getPostalCode() != null) address.setPostalCode(request.getPostalCode());
        if (request.getCountry() != null) address.setCountry(request.getCountry());
        if (request.getIsDefault() != null) address.setIsDefault(request.getIsDefault());

        Address saved = addressRepository.save(address);
        log.info("Updated address id={} for keycloakId={}", addressId, keycloakId);
        return mapAddressToResponse(saved);
    }

    /**
     * Delete an address.
     */
    public void deleteAddress(String keycloakId, Long addressId) {
        UserProfile profile = findProfileByKeycloakId(keycloakId);

        Address address = addressRepository.findByIdAndUserProfileId(addressId, profile.getId())
                .orElseThrow(() -> new AddressNotFoundException(
                        "Address not found: id=" + addressId));

        addressRepository.delete(address);
        log.info("Deleted address id={} for keycloakId={}", addressId, keycloakId);
    }

    /**
     * Set an address as default.
     */
    public AddressResponse setDefaultAddress(String keycloakId, Long addressId) {
        UserProfile profile = findProfileByKeycloakId(keycloakId);

        Address address = addressRepository.findByIdAndUserProfileId(addressId, profile.getId())
                .orElseThrow(() -> new AddressNotFoundException(
                        "Address not found: id=" + addressId));

        addressRepository.clearDefaultAddress(profile.getId());
        address.setIsDefault(true);
        Address saved = addressRepository.save(address);
        return mapAddressToResponse(saved);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UserProfile findProfileByKeycloakId(String keycloakId) {
        return userProfileRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new UserProfileNotFoundException(
                        "User profile not found for keycloakId: " + keycloakId));
    }

    private UserProfileResponse mapToResponse(UserProfile profile) {
        List<AddressResponse> addressResponses = profile.getAddresses() != null
                ? profile.getAddresses().stream()
                    .map(this::mapAddressToResponse)
                    .collect(Collectors.toList())
                : List.of();

        return UserProfileResponse.builder()
                .id(profile.getId())
                .keycloakId(profile.getKeycloakId())
                .email(profile.getEmail())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .phoneNumber(profile.getPhoneNumber())
                .profilePictureUrl(profile.getProfilePictureUrl())
                .role(profile.getRole())
                .isActive(profile.getIsActive())
                .addresses(addressResponses)
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    private AddressResponse mapAddressToResponse(Address address) {
        return AddressResponse.builder()
                .id(address.getId())
                .addressLabel(address.getAddressLabel())
                .fullName(address.getFullName())
                .phone(address.getPhone())
                .streetAddress(address.getStreetAddress())
                .city(address.getCity())
                .state(address.getState())
                .postalCode(address.getPostalCode())
                .country(address.getCountry())
                .isDefault(address.getIsDefault())
                .createdAt(address.getCreatedAt())
                .updatedAt(address.getUpdatedAt())
                .build();
    }
}

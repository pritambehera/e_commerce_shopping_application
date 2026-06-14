package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.dto.*;
import com.ecommerce.userservice.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * All requests arrive via the API Gateway (port 9000).
 * The Gateway validates the JWT and injects the Keycloak user ID
 * as the X-User-Id header before forwarding to this service (port 8083).
 *
 * Routes registered on the Gateway:
 *   /api/users/**  →  user-service
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserProfileController {

    private final UserProfileService userProfileService;

    // ── Current User (CUSTOMER / any authenticated user) ─────────────────────

    /**
     * GET /api/users/myProfile
     * Returns the profile of the currently authenticated user.
     *
     * Flow: Client (JWT) → api-gateway → user-service → get/update profile
     */
    @GetMapping("/myProfile")
    public ResponseEntity<UserProfileResponse> getMyProfile(
            @RequestHeader("X-User-Id") String keycloakId) {
        log.debug("GET /myProfile for keycloakId={}", keycloakId);
        return ResponseEntity.ok(userProfileService.getProfileByKeycloakId(keycloakId));
    }

    /**
     * PUT /api/users/updateMyProfile
     * Update own profile fields (name, phone, avatar URL).
     */
    @PutMapping("/updateMyProfile")
    public ResponseEntity<UserProfileResponse> updateMyProfile(
            @RequestHeader("X-User-Id") String keycloakId,
            @RequestBody @Valid UpdateProfileRequest request) {
        log.debug("PUT /updateMyProfile for keycloakId={}", keycloakId);
        return ResponseEntity.ok(userProfileService.updateProfile(keycloakId, request));
    }

    // ── Address Management ────────────────────────────────────────────────────

    /**
     * GET /api/users/myProfile/addresses
     * Returns all saved addresses for the current user.
     */
    @GetMapping("/myProfile/addresses")
    public ResponseEntity<List<AddressResponse>> getMyAddresses(
            @RequestHeader("X-User-Id") String keycloakId) {
        return ResponseEntity.ok(userProfileService.getAddresses(keycloakId));
    }

    /**
     * POST /api/users/myProfile/addresses
     * Add a new delivery address.
     */
    @PostMapping("/myProfile/addresses")
    public ResponseEntity<AddressResponse> addAddress(
            @RequestHeader("X-User-Id") String keycloakId,
            @RequestBody @Valid AddressRequest request) {
        AddressResponse created = userProfileService.addAddress(keycloakId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * PUT /api/users/myProfile/addresses/{addressId}
     * Update an existing address.
     */
    @PutMapping("/myProfile/addresses/{addressId}")
    public ResponseEntity<AddressResponse> updateAddress(
            @RequestHeader("X-User-Id") String keycloakId,
            @PathVariable Long addressId,
            @RequestBody @Valid AddressRequest request) {
        return ResponseEntity.ok(userProfileService.updateAddress(keycloakId, addressId, request));
    }

    /**
     * DELETE /api/users/myProfile/addresses/{addressId}
     * Remove an address.
     */
    @DeleteMapping("/myProfile/addresses/{addressId}")
    public ResponseEntity<Void> deleteAddress(
            @RequestHeader("X-User-Id") String keycloakId,
            @PathVariable Long addressId) {
        userProfileService.deleteAddress(keycloakId, addressId);
        return ResponseEntity.noContent().build();
    }

    /**
     * PATCH /api/users/myProfile/addresses/{addressId}/default
     * Mark an address as the default delivery address.
     */
    @PatchMapping("/myProfile/addresses/{addressId}/default")
    public ResponseEntity<AddressResponse> setDefaultAddress(
            @RequestHeader("X-User-Id") String keycloakId,
            @PathVariable Long addressId) {
        return ResponseEntity.ok(userProfileService.setDefaultAddress(keycloakId, addressId));
    }

    // ── Admin Endpoints ───────────────────────────────────────────────────────
    // (API Gateway should enforce ROLE_ADMIN on these routes)

    /**
     * GET /api/users
     * Admin: list all user profiles.
     */
    @GetMapping
    public ResponseEntity<List<UserProfileResponse>> getAllUsers() {
        return ResponseEntity.ok(userProfileService.getAllProfiles());
    }

    /**
     * GET /api/users/{id}
     * Admin: get a specific user by internal DB ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserProfileResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userProfileService.getProfileById(id));
    }

    /**
     * PATCH /api/users/{id}/suspend
     * Admin: suspend a user account.
     */
    @PatchMapping("/{id}/suspend")
    public ResponseEntity<UserProfileResponse> suspendUser(@PathVariable Long id) {
        return ResponseEntity.ok(userProfileService.setUserActiveStatus(id, false));
    }

    /**
     * PATCH /api/users/{id}/activate
     * Admin: reactivate a suspended user account.
     */
    @PatchMapping("/{id}/activate")
    public ResponseEntity<UserProfileResponse> activateUser(@PathVariable Long id) {
        return ResponseEntity.ok(userProfileService.setUserActiveStatus(id, true));
    }
}

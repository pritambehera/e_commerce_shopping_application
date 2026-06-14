package com.ecommerce.userservice;

import com.ecommerce.userservice.dto.*;
import com.ecommerce.userservice.entity.Address;
import com.ecommerce.userservice.entity.UserProfile;
import com.ecommerce.userservice.entity.UserRole;
import com.ecommerce.userservice.event.UserRegisteredEvent;
import com.ecommerce.userservice.exception.AddressNotFoundException;
import com.ecommerce.userservice.exception.UserProfileNotFoundException;
import com.ecommerce.userservice.repository.AddressRepository;
import com.ecommerce.userservice.repository.UserProfileRepository;
import com.ecommerce.userservice.service.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private AddressRepository addressRepository;

    @InjectMocks
    private UserProfileService userProfileService;

    private UserProfile sampleProfile;
    private final String KEYCLOAK_ID = "kc-user-123";

    @BeforeEach
    void setUp() {
        sampleProfile = UserProfile.builder()
                .id(1L)
                .keycloakId(KEYCLOAK_ID)
                .email("test@example.com")
                .firstName("John")
                .lastName("Doe")
                .role(UserRole.CUSTOMER)
                .isActive(true)
                .addresses(new ArrayList<>())
                .build();
    }

    // ── createProfileFromEvent ────────────────────────────────────────────────

    @Test
    @DisplayName("createProfileFromEvent: creates new profile when keycloakId not exists")
    void createProfileFromEvent_success() {
        UserRegisteredEvent event = new UserRegisteredEvent("kc-new-user", "Jane Smith", "jane@example.com");
        when(userProfileRepository.existsByKeycloakId("kc-new-user")).thenReturn(false);
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        userProfileService.createProfileFromEvent(event);

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(captor.capture());
        UserProfile saved = captor.getValue();
        assertThat(saved.getKeycloakId()).isEqualTo("kc-new-user");
        assertThat(saved.getEmail()).isEqualTo("jane@example.com");
        assertThat(saved.getFirstName()).isEqualTo("Jane");
        assertThat(saved.getLastName()).isEqualTo("Smith");
        assertThat(saved.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(saved.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("createProfileFromEvent: skips if profile already exists")
    void createProfileFromEvent_alreadyExists_skips() {
        UserRegisteredEvent event = new UserRegisteredEvent(KEYCLOAK_ID, "John Doe", "john@example.com");
        when(userProfileRepository.existsByKeycloakId(KEYCLOAK_ID)).thenReturn(true);

        userProfileService.createProfileFromEvent(event);

        verify(userProfileRepository, never()).save(any());
    }

    // ── getProfileByKeycloakId ────────────────────────────────────────────────

    @Test
    @DisplayName("getProfileByKeycloakId: returns profile when found")
    void getProfileByKeycloakId_found() {
        when(userProfileRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(sampleProfile));

        UserProfileResponse response = userProfileService.getProfileByKeycloakId(KEYCLOAK_ID);

        assertThat(response.getKeycloakId()).isEqualTo(KEYCLOAK_ID);
        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getFirstName()).isEqualTo("John");
    }

    @Test
    @DisplayName("getProfileByKeycloakId: throws UserProfileNotFoundException when not found")
    void getProfileByKeycloakId_notFound_throws() {
        when(userProfileRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userProfileService.getProfileByKeycloakId(KEYCLOAK_ID))
                .isInstanceOf(UserProfileNotFoundException.class)
                .hasMessageContaining(KEYCLOAK_ID);
    }

    // ── updateProfile ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateProfile: updates allowed fields only")
    void updateProfile_success() {
        when(userProfileRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(sampleProfile));
        when(userProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest req = new UpdateProfileRequest("Jane", "Doe", "9876543210", null);
        UserProfileResponse response = userProfileService.updateProfile(KEYCLOAK_ID, req);

        assertThat(response.getFirstName()).isEqualTo("Jane");
        assertThat(response.getPhoneNumber()).isEqualTo("9876543210");
        verify(userProfileRepository).save(sampleProfile);
    }

    @Test
    @DisplayName("updateProfile: throws when profile not found")
    void updateProfile_notFound_throws() {
        when(userProfileRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userProfileService.updateProfile(KEYCLOAK_ID, new UpdateProfileRequest()))
                .isInstanceOf(UserProfileNotFoundException.class);
    }

    // ── addAddress ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("addAddress: saves address linked to user profile")
    void addAddress_success() {
        when(userProfileRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(sampleProfile));
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> {
            Address a = inv.getArgument(0);
            a.setId(10L);
            return a;
        });

        AddressRequest req = AddressRequest.builder()
                .fullName("John Doe").phone("9999999999")
                .streetAddress("123 Main St").city("Vijayawada")
                .state("Andhra Pradesh").postalCode("520001")
                .isDefault(false).build();

        AddressResponse response = userProfileService.addAddress(KEYCLOAK_ID, req);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getCity()).isEqualTo("Vijayawada");
        assertThat(response.getCountry()).isEqualTo("India");
    }

    @Test
    @DisplayName("addAddress: clears existing default when new address is default")
    void addAddress_clearsOldDefault_whenNewIsDefault() {
        when(userProfileRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(sampleProfile));
        when(addressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AddressRequest req = AddressRequest.builder()
                .fullName("John").phone("9999").streetAddress("456 New St")
                .city("Hyderabad").state("Telangana").postalCode("500001")
                .isDefault(true).build();

        userProfileService.addAddress(KEYCLOAK_ID, req);

        verify(addressRepository).clearDefaultAddress(sampleProfile.getId());
    }

    // ── updateAddress ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateAddress: updates address when it belongs to user")
    void updateAddress_success() {
        Address existing = Address.builder()
                .id(5L).userProfile(sampleProfile)
                .fullName("Old Name").phone("1111")
                .streetAddress("Old St").city("Old City")
                .state("Old State").postalCode("000000")
                .country("India").isDefault(false).build();

        when(userProfileRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(sampleProfile));
        when(addressRepository.findByIdAndUserProfileId(5L, 1L)).thenReturn(Optional.of(existing));
        when(addressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AddressRequest req = AddressRequest.builder().city("New City").build();
        AddressResponse response = userProfileService.updateAddress(KEYCLOAK_ID, 5L, req);

        assertThat(response.getCity()).isEqualTo("New City");
    }

    @Test
    @DisplayName("updateAddress: throws AddressNotFoundException when address not found")
    void updateAddress_addressNotFound_throws() {
        when(userProfileRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(sampleProfile));
        when(addressRepository.findByIdAndUserProfileId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userProfileService.updateAddress(KEYCLOAK_ID, 99L, new AddressRequest()))
                .isInstanceOf(AddressNotFoundException.class);
    }

    // ── deleteAddress ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteAddress: deletes address when it belongs to user")
    void deleteAddress_success() {
        Address existing = Address.builder().id(5L).userProfile(sampleProfile).build();
        when(userProfileRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(sampleProfile));
        when(addressRepository.findByIdAndUserProfileId(5L, 1L)).thenReturn(Optional.of(existing));

        userProfileService.deleteAddress(KEYCLOAK_ID, 5L);

        verify(addressRepository).delete(existing);
    }

    @Test
    @DisplayName("deleteAddress: throws when address does not belong to user")
    void deleteAddress_wrongUser_throws() {
        when(userProfileRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(sampleProfile));
        when(addressRepository.findByIdAndUserProfileId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userProfileService.deleteAddress(KEYCLOAK_ID, 99L))
                .isInstanceOf(AddressNotFoundException.class);
    }

    // ── setDefaultAddress ─────────────────────────────────────────────────────

    @Test
    @DisplayName("setDefaultAddress: marks address as default and clears old default")
    void setDefaultAddress_success() {
        Address address = Address.builder().id(3L).userProfile(sampleProfile).isDefault(false).build();
        when(userProfileRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(sampleProfile));
        when(addressRepository.findByIdAndUserProfileId(3L, 1L)).thenReturn(Optional.of(address));
        when(addressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AddressResponse response = userProfileService.setDefaultAddress(KEYCLOAK_ID, 3L);

        verify(addressRepository).clearDefaultAddress(sampleProfile.getId());
        assertThat(address.getIsDefault()).isTrue();
    }

    // ── setUserActiveStatus ───────────────────────────────────────────────────

    @Test
    @DisplayName("setUserActiveStatus: admin can suspend user")
    void setUserActiveStatus_suspend() {
        when(userProfileRepository.findById(1L)).thenReturn(Optional.of(sampleProfile));
        when(userProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserProfileResponse response = userProfileService.setUserActiveStatus(1L, false);

        assertThat(response.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("setUserActiveStatus: admin can activate user")
    void setUserActiveStatus_activate() {
        sampleProfile.setIsActive(false);
        when(userProfileRepository.findById(1L)).thenReturn(Optional.of(sampleProfile));
        when(userProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserProfileResponse response = userProfileService.setUserActiveStatus(1L, true);

        assertThat(response.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("setUserActiveStatus: throws when user not found")
    void setUserActiveStatus_notFound_throws() {
        when(userProfileRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userProfileService.setUserActiveStatus(999L, false))
                .isInstanceOf(UserProfileNotFoundException.class);
    }
}

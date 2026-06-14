package com.ecommerce.userservice.repository;

import com.ecommerce.userservice.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByUserProfileId(Long userProfileId);

    Optional<Address> findByIdAndUserProfileId(Long id, Long userProfileId);

    Optional<Address> findByUserProfileIdAndIsDefaultTrue(Long userProfileId);

    @Modifying
    @Query("UPDATE Address a SET a.isDefault = false WHERE a.userProfile.id = :userProfileId")
    void clearDefaultAddress(Long userProfileId);
}

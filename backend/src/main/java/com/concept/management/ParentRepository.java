package com.concept.management;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParentRepository extends JpaRepository<Parent, UUID> {
    Optional<Parent> findByFirstNameIgnoreCase(String firstName);
    Optional<Parent> findByUserId(UUID userId);
    Optional<Parent> findByTenantIdAndPhoneNumber(UUID tenantId, String phoneNumber);
    // List variant: some tenants already have duplicate guardians on one phone
    // (from before dedup existed), so the Optional query would throw on >1 row.
    java.util.List<Parent> findAllByTenantIdAndPhoneNumber(UUID tenantId, String phoneNumber);
}

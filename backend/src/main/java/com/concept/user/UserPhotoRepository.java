package com.concept.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserPhotoRepository extends JpaRepository<UserPhoto, UUID> {

    /**
     * The only read a caller should use. Serving a photo has to be scoped to the
     * caller's own school, so the tenant is part of the lookup rather than
     * something the caller is trusted to have checked — a bare findById here
     * would hand back any school's photograph for a guessed id.
     */
    Optional<UserPhoto> findByUserIdAndTenantId(UUID userId, UUID tenantId);

    void deleteByUserId(UUID userId);
}

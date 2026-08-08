package com.concept.messaging.data;

import com.concept.common.TenantScopedRepository;
import com.concept.user.User;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped user access for resolving the acting user and message participants. */
@Repository
public interface MsgUserRepository extends TenantScopedRepository<User, UUID> {

    Optional<User> findByEmail(String email);
}

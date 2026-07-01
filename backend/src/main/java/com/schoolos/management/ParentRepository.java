package com.schoolos.management;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParentRepository extends JpaRepository<Parent, UUID> {
    Optional<Parent> findByFirstNameIgnoreCase(String firstName);
    Optional<Parent> findByUserId(UUID userId);
}

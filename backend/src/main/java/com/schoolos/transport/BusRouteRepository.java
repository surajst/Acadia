package com.schoolos.transport;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusRouteRepository extends JpaRepository<BusRoute, UUID> {

    List<BusRoute> findByTenantId(UUID tenantId);

    Optional<BusRoute> findByDriverId(UUID driverId);
}

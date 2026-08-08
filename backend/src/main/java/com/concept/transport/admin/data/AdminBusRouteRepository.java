package com.concept.transport.admin.data;

import com.concept.common.TenantScopedRepository;
import com.concept.transport.BusRoute;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Tenant-scoped bus-route access for the admin transport console. Assignments
 * resolve the route via {@code findByIdAndTenantId}, so one school can never
 * reassign another school's route (ADR 0001).
 */
@Repository
public interface AdminBusRouteRepository extends TenantScopedRepository<BusRoute, UUID> {

    List<BusRoute> findByTenantId(UUID tenantId);
}

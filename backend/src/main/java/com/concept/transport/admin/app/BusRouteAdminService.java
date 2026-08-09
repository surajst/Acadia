package com.concept.transport.admin.app;

import com.concept.common.AuditLogService;
import com.concept.shared.data.ClassSection;
import com.concept.transport.BusRoute;
import com.concept.transport.admin.data.AdminBusClassSectionRepository;
import com.concept.transport.admin.data.AdminBusRouteRepository;
import com.concept.transport.admin.data.AdminBusStaffRepository;
import com.concept.user.User;
import com.concept.user.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application layer for admin transport management: listing routes, adding
 * routes, assigning a driver to a route, and attaching a route to a class
 * section. Owns the tenant checks and audit trail; every id lookup is
 * tenant-scoped so assignments can never cross tenants (ADR 0001).
 */
@Service
public class BusRouteAdminService {

    private final AdminBusRouteRepository busRouteRepository;
    private final AdminBusStaffRepository staffRepository;
    private final AdminBusClassSectionRepository classSectionRepository;
    private final AuditLogService auditLogService;

    public BusRouteAdminService(AdminBusRouteRepository busRouteRepository,
                                AdminBusStaffRepository staffRepository,
                                AdminBusClassSectionRepository classSectionRepository,
                                AuditLogService auditLogService) {
        this.busRouteRepository = busRouteRepository;
        this.staffRepository = staffRepository;
        this.classSectionRepository = classSectionRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<BusRouteView> listRoutes(UUID tenantId) {
        if (tenantId == null) {
            return Collections.emptyList();
        }
        return busRouteRepository.findByTenantId(tenantId).stream()
                .map(r -> new BusRouteView(r.getId(), r.getName(), r.getDriverId(),
                        r.getCurrentLatitude(), r.getCurrentLongitude(), r.getLastPingAt()))
                .collect(Collectors.toList());
    }

    @Transactional
    public UUID addRoute(String name, UUID tenantId, UUID academicYearId, Authentication authentication) {
        BusRoute route = new BusRoute();
        route.setId(UUID.randomUUID());
        route.setTenantId(tenantId);
        route.setAcademicYearId(academicYearId);
        route.setName(name);
        busRouteRepository.save(route);
        auditLogService.log(authentication, "BUS_ROUTE_ADDED", "BusRoute", route.getId(), "Added bus route " + name);
        return route.getId();
    }

    /** Assign a DRIVER to a route (both resolved tenant-scoped). Throws on a missing route/driver. */
    @Transactional
    public void assignDriver(UUID routeId, UUID driverId, UUID tenantId, Authentication authentication) {
        BusRoute route = busRouteRepository.findByIdAndTenantId(routeId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Bus route not found"));
        User driver = staffRepository.findByIdAndTenantId(driverId, tenantId).orElse(null);
        if (driver == null || driver.getRole() != UserRole.DRIVER) {
            throw new IllegalArgumentException("Driver not found");
        }
        route.setDriverId(driverId);
        busRouteRepository.save(route);
        auditLogService.log(authentication, "BUS_ROUTE_DRIVER_ASSIGNED", "BusRoute", route.getId(),
                "Assigned driver " + driver.getFullName() + " to route " + route.getName());
    }

    /** Attach a bus route to a class section (both resolved tenant-scoped). Throws on a missing section/route. */
    @Transactional
    public void assignClassSectionRoute(UUID sectionId, UUID busRouteId, UUID tenantId, Authentication authentication) {
        ClassSection section = classSectionRepository.findByIdAndTenantId(sectionId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Class section not found"));
        BusRoute route = busRouteRepository.findByIdAndTenantId(busRouteId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Bus route not found"));
        section.setBusRouteId(busRouteId);
        classSectionRepository.save(section);
        auditLogService.log(authentication, "CLASS_SECTION_BUS_ROUTE_ASSIGNED", "ClassSection", section.getId(),
                "Assigned bus route " + route.getName() + " to " + section.getGradeName() + " - " + section.getSectionName());
    }
}

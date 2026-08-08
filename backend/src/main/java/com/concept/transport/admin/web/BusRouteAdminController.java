package com.concept.transport.admin.web;

import com.concept.tenant.TenantContext;
import com.concept.transport.admin.app.BusRouteAdminService;
import com.concept.transport.admin.app.BusRouteView;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Interface layer for admin transport management. Binds requests, resolves the
 * tenant, and returns flat JSON — no entities, no persistence (ADR 0001).
 */
@Controller
public class BusRouteAdminController {

    private final BusRouteAdminService busRouteAdminService;
    private final TenantContext tenantContext;

    public BusRouteAdminController(BusRouteAdminService busRouteAdminService, TenantContext tenantContext) {
        this.busRouteAdminService = busRouteAdminService;
        this.tenantContext = tenantContext;
    }

    @GetMapping("/web/admin/bus-routes")
    @ResponseBody
    public List<BusRouteView> listBusRoutes() {
        return busRouteAdminService.listRoutes(tenantContext.getTenantId().orElse(null));
    }

    @PostMapping("/web/admin/bus-routes/add")
    @ResponseBody
    public Object addBusRoute(@RequestParam("name") String name, Authentication authentication) {
        UUID id = busRouteAdminService.addRoute(name,
                tenantContext.getTenantId().orElse(null), tenantContext.getAcademicYearId().orElse(null),
                authentication);
        return Map.of("status", "created", "id", id);
    }

    @PostMapping("/web/admin/bus-routes/{id}/assign-driver")
    @ResponseBody
    public Object assignBusRouteDriver(@PathVariable("id") UUID id,
                                       @RequestParam("driverId") UUID driverId,
                                       Authentication authentication) {
        try {
            busRouteAdminService.assignDriver(id, driverId, tenantContext.getTenantId().orElse(null), authentication);
            return Map.of("status", "assigned");
        } catch (IllegalArgumentException e) {
            return Map.of("error", e.getMessage());
        }
    }

    @PostMapping("/web/admin/class-sections/{id}/assign-bus-route")
    @ResponseBody
    public Object assignClassSectionBusRoute(@PathVariable("id") UUID id,
                                             @RequestParam("busRouteId") UUID busRouteId,
                                             Authentication authentication) {
        try {
            busRouteAdminService.assignClassSectionRoute(id, busRouteId,
                    tenantContext.getTenantId().orElse(null), authentication);
            return Map.of("status", "assigned");
        } catch (IllegalArgumentException e) {
            return Map.of("error", e.getMessage());
        }
    }
}

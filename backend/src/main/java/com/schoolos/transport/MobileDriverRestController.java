package com.schoolos.transport;

import com.schoolos.user.CurrentUserService;
import com.schoolos.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

// Every endpoint here resolves the driver's own BusRoute via
// findByDriverId(currentUserId) — the client never supplies a route ID, so
// there's no ID to tamper with. A driver can only ever affect the one route
// an admin pre-assigned to their own user account.
@RestController
@RequestMapping("/api/mobile/driver")
@PreAuthorize("hasRole('DRIVER')")
public class MobileDriverRestController {

    @Autowired
    private BusRouteRepository busRouteRepository;

    @Autowired
    private CurrentUserService currentUserService;

    @GetMapping("/route/my-route")
    public ResponseEntity<?> getMyRoute(Authentication authentication) {
        User me = currentUserService.getCurrentUser(authentication).orElse(null);
        if (me == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        BusRoute route = busRouteRepository.findByDriverId(me.getId()).orElse(null);
        if (route == null) {
            return ResponseEntity.ok(Map.of("assigned", false));
        }

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("assigned", true);
        response.put("routeId", route.getId());
        response.put("routeName", route.getName());
        response.put("latitude", route.getCurrentLatitude());
        response.put("longitude", route.getCurrentLongitude());
        response.put("lastPingAt", route.getLastPingAt());
        return ResponseEntity.ok(response);
    }

    public static class LocationPingRequest {
        public Double latitude;
        public Double longitude;
    }

    @PostMapping("/location/ping")
    public ResponseEntity<?> pingLocation(@RequestBody LocationPingRequest request, Authentication authentication) {
        User me = currentUserService.getCurrentUser(authentication).orElse(null);
        if (me == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        if (request == null || request.latitude == null || request.longitude == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "latitude and longitude are required"));
        }

        BusRoute route = busRouteRepository.findByDriverId(me.getId()).orElse(null);
        if (route == null) {
            return ResponseEntity.status(404).body(Map.of("error", "No bus route assigned to this driver"));
        }

        route.setCurrentLatitude(request.latitude);
        route.setCurrentLongitude(request.longitude);
        route.setLastPingAt(Instant.now());
        busRouteRepository.save(route);

        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}

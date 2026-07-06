package com.schoolos.transport;

import com.schoolos.management.ClassSection;
import com.schoolos.management.ClassSectionRepository;
import com.schoolos.management.Parent;
import com.schoolos.management.ParentRepository;
import com.schoolos.management.Student;
import com.schoolos.management.StudentRepository;
import com.schoolos.management.AdminManagementController;
import com.schoolos.management.MobileParentRestController;
import com.schoolos.tenant.AcademicYear;
import com.schoolos.tenant.AcademicYearRepository;
import com.schoolos.tenant.Tenant;
import com.schoolos.tenant.TenantRepository;
import com.schoolos.user.User;
import com.schoolos.user.UserRepository;
import com.schoolos.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Covers the bus-tracking feature's tenant/ownership boundaries: a driver
// can only ever ping/read the one route pre-assigned to their own user
// account (no route ID is ever client-supplied), a parent can only read
// bus location for their own linked child, and admin assignment endpoints
// reject cross-tenant driver/route/class-section combinations.
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
public class BusRouteTenantTest {

    @Autowired
    private MobileDriverRestController driverController;

    @Autowired
    private MobileParentRestController parentController;

    @Autowired
    private AdminManagementController adminController;

    @Autowired
    private BusRouteRepository busRouteRepository;

    @Autowired
    private ClassSectionRepository classSectionRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ParentRepository parentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private AcademicYearRepository academicYearRepository;

    private UUID tenantA;
    private UUID tenantB;
    private UUID academicYearIdA;
    private UUID academicYearIdB;
    private User driverA;
    private User driverB;
    private BusRoute routeA;
    private BusRoute routeB;
    private ClassSection sectionA;
    private Student studentA;
    private Authentication asDriverA;
    private Authentication asDriverB;
    private Authentication asAdminA;

    private Tenant makeTenant() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Test Tenant " + tenant.getId());
        tenant.setSubdomain("test-" + tenant.getId());
        tenant.setActive(true);
        tenant.setCreatedAt(Instant.now());
        return tenantRepository.saveAndFlush(tenant);
    }

    private UUID makeAcademicYear(UUID tenantId) {
        AcademicYear year = new AcademicYear();
        year.setId(UUID.randomUUID());
        year.setTenantId(tenantId);
        year.setName("2026");
        year.setStartDate(LocalDate.of(2026, 1, 1));
        year.setEndDate(LocalDate.of(2026, 12, 31));
        year.setCurrent(true);
        return academicYearRepository.saveAndFlush(year).getId();
    }

    private User makeUser(UUID tenantId, UUID academicYearId, UserRole role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setTenantId(tenantId);
        user.setAcademicYearId(academicYearId);
        user.setEmail(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("hash");
        user.setFullName(role.name() + " User");
        user.setRole(role);
        user.setActive(true);
        return userRepository.saveAndFlush(user);
    }

    private BusRoute makeRoute(UUID tenantId, UUID academicYearId, String name, UUID driverId) {
        BusRoute route = new BusRoute();
        route.setId(UUID.randomUUID());
        route.setTenantId(tenantId);
        route.setAcademicYearId(academicYearId);
        route.setName(name);
        route.setDriverId(driverId);
        return busRouteRepository.saveAndFlush(route);
    }

    private Authentication authFor(User user) {
        return new UsernamePasswordAuthenticationToken(user.getEmail(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    }

    // @PreAuthorize reads from SecurityContextHolder, not the method's
    // Authentication parameter — must switch the context per-actor.
    private Authentication actAs(Authentication auth) {
        SecurityContextHolder.getContext().setAuthentication(auth);
        return auth;
    }

    @BeforeEach
    public void setup() {
        tenantA = makeTenant().getId();
        tenantB = makeTenant().getId();
        academicYearIdA = makeAcademicYear(tenantA);
        academicYearIdB = makeAcademicYear(tenantB);

        driverA = makeUser(tenantA, academicYearIdA, UserRole.DRIVER);
        driverB = makeUser(tenantB, academicYearIdB, UserRole.DRIVER);

        routeA = makeRoute(tenantA, academicYearIdA, "Route A", driverA.getId());
        routeB = makeRoute(tenantB, academicYearIdB, "Route B", driverB.getId());

        sectionA = new ClassSection();
        sectionA.setId(UUID.randomUUID());
        sectionA.setTenantId(tenantA);
        sectionA.setAcademicYearId(academicYearIdA);
        sectionA.setGradeName("Grade 1");
        sectionA.setSectionName("A");
        sectionA.setBusRouteId(routeA.getId());
        classSectionRepository.saveAndFlush(sectionA);

        studentA = new Student();
        studentA.setId(UUID.randomUUID());
        studentA.setTenantId(tenantA);
        studentA.setAcademicYearId(academicYearIdA);
        studentA.setFirstName("Test");
        studentA.setLastName("Student");
        studentA.setClassSection(sectionA);
        studentRepository.saveAndFlush(studentA);

        asDriverA = authFor(driverA);
        asDriverB = authFor(driverB);

        User adminA = makeUser(tenantA, academicYearIdA, UserRole.ADMIN);
        asAdminA = authFor(adminA);
        SecurityContextHolder.getContext().setAuthentication(asAdminA);
    }

    @Test
    public void driverCanPingAndReadOwnRoute() {
        MobileDriverRestController.LocationPingRequest ping = new MobileDriverRestController.LocationPingRequest();
        ping.latitude = 12.34;
        ping.longitude = 56.78;

        ResponseEntity<?> pingResponse = driverController.pingLocation(ping, actAs(asDriverA));
        assertEquals(200, pingResponse.getStatusCode().value());

        ResponseEntity<?> myRoute = driverController.getMyRoute(actAs(asDriverA));
        assertEquals(200, myRoute.getStatusCode().value());
        Map<?, ?> body = (Map<?, ?>) myRoute.getBody();
        assertEquals(true, body.get("assigned"));
        assertEquals(12.34, (Double) body.get("latitude"));
        assertEquals(56.78, (Double) body.get("longitude"));
    }

    @Test
    public void driverWithNoRouteAssigned_getsAssignedFalseAndPing404() {
        User unassignedDriver = makeUser(tenantA, academicYearIdA, UserRole.DRIVER);
        Authentication asUnassigned = authFor(unassignedDriver);

        ResponseEntity<?> myRoute = driverController.getMyRoute(actAs(asUnassigned));
        assertEquals(Map.of("assigned", false), myRoute.getBody());

        MobileDriverRestController.LocationPingRequest ping = new MobileDriverRestController.LocationPingRequest();
        ping.latitude = 1.0;
        ping.longitude = 2.0;
        ResponseEntity<?> pingResponse = driverController.pingLocation(ping, actAs(asUnassigned));
        assertEquals(404, pingResponse.getStatusCode().value());
    }

    @Test
    public void pingByDriverA_neverAffectsRouteBOwnedByDriverB() {
        MobileDriverRestController.LocationPingRequest ping = new MobileDriverRestController.LocationPingRequest();
        ping.latitude = 99.0;
        ping.longitude = 99.0;
        driverController.pingLocation(ping, actAs(asDriverA));

        BusRoute reloadedB = busRouteRepository.findById(routeB.getId()).orElseThrow();
        assertNull(reloadedB.getCurrentLatitude());
        assertNull(reloadedB.getCurrentLongitude());
    }

    @Test
    public void parentCanReadOwnChildBusLocation() {
        Parent parentA = new Parent();
        parentA.setId(UUID.randomUUID());
        parentA.setTenantId(tenantA);
        parentA.setAcademicYearId(academicYearIdA);
        parentA.setFirstName("Parent");
        parentA.setLastName("A");
        User parentUserA = makeUser(tenantA, academicYearIdA, UserRole.PARENT);
        parentA.setUserId(parentUserA.getId());
        parentRepository.saveAndFlush(parentA);
        studentA.getParents().add(parentA);
        studentRepository.saveAndFlush(studentA);

        MobileDriverRestController.LocationPingRequest ping = new MobileDriverRestController.LocationPingRequest();
        ping.latitude = 10.0;
        ping.longitude = 20.0;
        driverController.pingLocation(ping, actAs(asDriverA));

        Authentication asParentA = authFor(parentUserA);
        ResponseEntity<?> response = parentController.getBusLocation(studentA.getId(), actAs(asParentA));
        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals(true, body.get("assigned"));
        assertEquals("Route A", body.get("routeName"));
        assertEquals(10.0, (Double) body.get("latitude"));
    }

    @Test
    public void parentWithNoLinkedChildren_cannotReadAnyBusLocation() {
        User strangerParentUser = makeUser(tenantB, academicYearIdB, UserRole.PARENT);
        Authentication asStrangerParent = authFor(strangerParentUser);

        ResponseEntity<?> response = parentController.getBusLocation(studentA.getId(), asStrangerParent);
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    public void adminAssignDriver_rejectsNonDriverRoleUser() {
        User teacher = makeUser(tenantA, academicYearIdA, UserRole.TEACHER);
        Object result = adminController.assignBusRouteDriver(routeA.getId(), teacher.getId(), asAdminA);
        assertTrue(((Map<?, ?>) result).containsKey("error"));
    }

    @Test
    public void adminAssignDriver_rejectsCrossTenantDriver() {
        Object result = adminController.assignBusRouteDriver(routeA.getId(), driverB.getId(), asAdminA);
        assertTrue(((Map<?, ?>) result).containsKey("error"));
    }

    @Test
    public void adminAssignBusRoute_rejectsCrossTenantRoute() {
        Object result = adminController.assignClassSectionBusRoute(sectionA.getId(), routeB.getId(), asAdminA);
        assertTrue(((Map<?, ?>) result).containsKey("error"));

        ClassSection reloaded = classSectionRepository.findById(sectionA.getId()).orElseThrow();
        assertEquals(routeA.getId(), reloaded.getBusRouteId());
    }
}

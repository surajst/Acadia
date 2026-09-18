package com.concept.user;

import com.concept.tenant.AcademicYear;
import com.concept.tenant.AcademicYearRepository;
import com.concept.tenant.Tenant;
import com.concept.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Profile photographs: the round trip, the two ways a bad upload is refused,
 * and the tenant boundary on reading one back.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
public class ProfilePhotoTest {

    @Autowired private MobileProfileRestController controller;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;

    private User me;
    private User sameSchool;
    private User otherSchool;

    @BeforeEach
    public void setup() {
        UUID tenantA = newTenant();
        UUID tenantB = newTenant();
        me = newUser(tenantA, "me");
        sameSchool = newUser(tenantA, "colleague");
        otherSchool = newUser(tenantB, "stranger");
    }

    private UUID newTenant() {
        UUID id = UUID.randomUUID();
        Tenant t = new Tenant();
        t.setId(id);
        t.setName("Photo School");
        t.setSubdomain("photo-" + id.toString().substring(0, 8));
        t.setActive(true);
        t.setCreatedAt(Instant.now());
        tenantRepository.saveAndFlush(t);

        AcademicYear y = new AcademicYear();
        y.setId(UUID.randomUUID());
        y.setTenantId(id);
        y.setName("2026-27");
        y.setStartDate(LocalDate.of(2026, 4, 1));
        y.setEndDate(LocalDate.of(2027, 3, 31));
        y.setCurrent(true);
        academicYearRepository.saveAndFlush(y);
        yearByTenant.put(id, y.getId());
        return id;
    }

    private final java.util.Map<UUID, UUID> yearByTenant = new java.util.HashMap<>();

    private User newUser(UUID tenantId, String tag) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setTenantId(tenantId);
        u.setAcademicYearId(yearByTenant.get(tenantId));
        u.setEmail(tag + "-" + UUID.randomUUID().toString().substring(0, 8) + "@school");
        u.setPasswordHash("x");
        u.setFullName("Test Person");
        u.setRole(UserRole.TEACHER);
        u.setActive(true);
        return userRepository.saveAndFlush(u);
    }

    private Authentication as(User u) {
        return new UsernamePasswordAuthenticationToken(u.getEmail(), "x",
                List.of(new SimpleGrantedAuthority("ROLE_TEACHER")));
    }

    private MockMultipartFile jpeg(byte[] bytes) {
        return new MockMultipartFile("photo", "me.jpg", MediaType.IMAGE_JPEG_VALUE, bytes);
    }

    @Test
    public void aPhotoSurvivesTheRoundTrip() {
        byte[] bytes = "not-really-a-jpeg-but-bytes-are-bytes".getBytes();

        ResponseEntity<?> saved = controller.uploadPhoto(jpeg(bytes), as(me));
        assertEquals(200, saved.getStatusCode().value());

        ResponseEntity<byte[]> fetched = controller.photo(me.getId(), as(me));
        assertEquals(200, fetched.getStatusCode().value());
        assertArrayEquals(bytes, fetched.getBody(), "the bytes read back must be the bytes sent");
        assertEquals(MediaType.IMAGE_JPEG, fetched.getHeaders().getContentType());
    }

    @Test
    public void theProfilePayloadAdvertisesThePhoto() {
        assertEquals(Boolean.FALSE, profileField(me, "hasPhoto"), "no photo to begin with");

        controller.uploadPhoto(jpeg("bytes".getBytes()), as(me));

        assertEquals(Boolean.TRUE, profileField(me, "hasPhoto"));
        assertNotNull(profileField(me, "photoUpdatedAt"),
                "the app builds its cache-busting URL from this");
    }

    @SuppressWarnings("unchecked")
    private Object profileField(User u, String key) {
        ResponseEntity<?> res = controller.getUserProfile(as(u));
        return ((java.util.Map<String, Object>) res.getBody()).get(key);
    }

    @Test
    public void aColleagueInTheSameSchoolCanBeSeen() {
        controller.uploadPhoto(jpeg("bytes".getBytes()), as(sameSchool));

        assertEquals(200, controller.photo(sameSchool.getId(), as(me)).getStatusCode().value(),
                "a photo within one's own school is readable -- a parent sees their child's teacher");
    }

    @Test
    public void anotherSchoolsPhotoIsNotReadable() {
        controller.uploadPhoto(jpeg("bytes".getBytes()), as(otherSchool));

        assertEquals(404, controller.photo(otherSchool.getId(), as(me)).getStatusCode().value(),
                "and it must be 404, not 403 -- otherwise the endpoint reports which ids exist");
    }

    @Test
    public void aMissingPhotoIsTheSameAnswerAsAForbiddenOne() {
        assertEquals(404, controller.photo(sameSchool.getId(), as(me)).getStatusCode().value());
        assertEquals(404, controller.photo(UUID.randomUUID(), as(me)).getStatusCode().value());
    }

    @Test
    public void onlyImagesAreAccepted() {
        MockMultipartFile pdf = new MockMultipartFile("photo", "cv.pdf", "application/pdf", "%PDF".getBytes());

        assertEquals(400, controller.uploadPhoto(pdf, as(me)).getStatusCode().value());
        assertEquals(404, controller.photo(me.getId(), as(me)).getStatusCode().value(),
                "a refused upload must not have written anything");
    }

    @Test
    public void anOversizedImageIsRefusedWithAReadableMessage() {
        byte[] huge = new byte[1_600_000];

        ResponseEntity<?> res = controller.uploadPhoto(jpeg(huge), as(me));

        assertEquals(400, res.getStatusCode().value());
        assertEquals(404, controller.photo(me.getId(), as(me)).getStatusCode().value());
    }

    @Test
    public void removingAPhotoClearsIt() {
        controller.uploadPhoto(jpeg("bytes".getBytes()), as(me));
        assertEquals(200, controller.photo(me.getId(), as(me)).getStatusCode().value());

        controller.deletePhoto(as(me));

        assertEquals(404, controller.photo(me.getId(), as(me)).getStatusCode().value());
        assertNull(userRepository.findByEmail(me.getEmail()).orElseThrow().getPhotoUpdatedAt());
    }

    @Test
    public void anUnauthenticatedCallerGetsNothing() {
        assertEquals(401, controller.uploadPhoto(jpeg("b".getBytes()), null).getStatusCode().value());
        assertEquals(401, controller.photo(me.getId(), null).getStatusCode().value());
        assertEquals(401, controller.deletePhoto(null).getStatusCode().value());
    }
}

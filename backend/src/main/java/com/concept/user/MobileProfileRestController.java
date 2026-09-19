package com.concept.user;

import com.concept.tenant.AcademicYearRepository;
import com.concept.tenant.TenantRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/mobile/user")
public class MobileProfileRestController {

    private final UserRepository userRepository;
    private final UserPhotoRepository userPhotoRepository;
    private final TenantRepository tenantRepository;
    private final AcademicYearRepository academicYearRepository;

    public MobileProfileRestController(UserRepository userRepository,
                                        UserPhotoRepository userPhotoRepository,
                                        TenantRepository tenantRepository,
                                        AcademicYearRepository academicYearRepository) {
        this.userRepository = userRepository;
        this.userPhotoRepository = userPhotoRepository;
        this.tenantRepository = tenantRepository;
        this.academicYearRepository = academicYearRepository;
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getUserProfile(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .map(user -> {
                    Map<String, Object> profile = new HashMap<>();
                    profile.put("email", user.getEmail());
                    profile.put("fullName", user.getFullName());
                    profile.put("role", user.getRole().name());

                    // Simple split for first/last name
                    String[] nameParts = user.getFullName().split(" ", 2);
                    profile.put("firstName", nameParts[0]);
                    profile.put("lastName", nameParts.length > 1 ? nameParts[1] : "");

                    String schoolName = user.getTenantId() != null
                            ? tenantRepository.findById(user.getTenantId()).map(t -> t.getName()).orElse(null)
                            : null;
                    profile.put("schoolName", schoolName);

                    String academicYearName = user.getAcademicYearId() != null
                            ? academicYearRepository.findById(user.getAcademicYearId()).map(y -> y.getName()).orElse(null)
                            : null;
                    profile.put("academicYearName", academicYearName);

                    // The id and the two photo fields are what let the app build
                    // a photo URL that changes when the photo does -- without
                    // photoUpdatedAt, a replaced picture would sit behind the
                    // cached copy of the old one.
                    profile.put("userId", user.getId());
                    profile.put("hasPhoto", user.getPhotoUpdatedAt() != null);
                    profile.put("photoUpdatedAt", user.getPhotoUpdatedAt());

                    return ResponseEntity.ok(profile);
                })
                .orElse(ResponseEntity.status(404).body(Map.of("error", "User not found")));
    }

    // ── Profile photograph ──────────────────────────────────────────────────

    /** Squares only, and only the two formats a phone camera roll produces. */
    private static final Set<String> ALLOWED_TYPES = Set.of(MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE);

    /**
     * Generous for a 256px avatar (~30KB) and still far under the multipart
     * ceiling, so a wrong-sized upload is refused with a sentence a person can
     * act on rather than a raw 413 from the container.
     */
    private static final long MAX_BYTES = 1_500_000;

    /**
     * Set the signed-in user's photograph.
     *
     * <p>The row written is always the caller's own: the identity comes from the
     * Authentication, never from the request, so there is no id here to tamper
     * with.
     */
    @PostMapping("/photo")
    @Transactional
    public ResponseEntity<?> uploadPhoto(@RequestParam("photo") MultipartFile photo, Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        if (photo == null || photo.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No image was uploaded"));
        }
        String contentType = photo.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            return ResponseEntity.badRequest().body(Map.of("error", "A profile picture must be a JPEG or PNG"));
        }
        if (photo.getSize() > MAX_BYTES) {
            return ResponseEntity.badRequest().body(Map.of("error", "That picture is too large. Please choose a smaller one."));
        }

        return userRepository.findByEmail(authentication.getName())
                .map(user -> {
                    Instant now = Instant.now();
                    try {
                        UserPhoto row = userPhotoRepository.findByUserIdAndTenantId(user.getId(), user.getTenantId())
                                .orElseGet(UserPhoto::new);
                        row.setUserId(user.getId());
                        row.setTenantId(user.getTenantId());
                        row.setContentType(contentType.toLowerCase());
                        row.setBytes(photo.getBytes());
                        row.setUpdatedAt(now);
                        userPhotoRepository.save(row);
                    } catch (IOException e) {
                        return ResponseEntity.status(400).body(Map.of("error", "Could not read that image"));
                    }
                    user.setPhotoUpdatedAt(now);
                    userRepository.save(user);
                    return ResponseEntity.ok(Map.of(
                            "status", "saved",
                            "userId", user.getId(),
                            "photoUpdatedAt", user.getPhotoUpdatedAt()));
                })
                .orElse(ResponseEntity.status(404).body(Map.of("error", "User not found")));
    }

    /**
     * Serve a photograph.
     *
     * <p>Scoped to the caller's own school: a parent may legitimately see their
     * child's teacher, but nobody should be able to walk user ids across
     * tenants. A photo in another school answers exactly as a missing one does,
     * so the endpoint cannot be used to probe which ids exist.
     */
    @GetMapping("/photo/{userId}")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> photo(@PathVariable UUID userId, Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }
        UUID callerTenant = userRepository.findByEmail(authentication.getName())
                .map(User::getTenantId).orElse(null);
        if (callerTenant == null) {
            return ResponseEntity.status(404).build();
        }

        return userPhotoRepository.findByUserIdAndTenantId(userId, callerTenant)
                .map(row -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(row.getContentType()))
                        // Safe to cache hard: the URL carries photoUpdatedAt, so
                        // a new photo is a new URL.
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                        .body(row.getBytes()))
                .orElse(ResponseEntity.status(404).build());
    }

    @DeleteMapping("/photo")
    @Transactional
    public ResponseEntity<?> deletePhoto(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        return userRepository.findByEmail(authentication.getName())
                .map(user -> {
                    userPhotoRepository.deleteByUserId(user.getId());
                    user.setPhotoUpdatedAt(null);
                    userRepository.save(user);
                    return ResponseEntity.ok(Map.of("status", "removed"));
                })
                .orElse(ResponseEntity.status(404).body(Map.of("error", "User not found")));
    }
}

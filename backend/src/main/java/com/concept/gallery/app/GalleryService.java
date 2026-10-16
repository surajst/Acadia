package com.concept.gallery.app;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.concept.gallery.app.GalleryViews.AlbumSummary;
import com.concept.gallery.app.GalleryViews.CreateAlbum;
import com.concept.gallery.app.GalleryViews.PhotoSummary;
import com.concept.gallery.app.GalleryViews.PupilWithoutConsent;
import com.concept.gallery.data.AlbumPhotoRepository;
import com.concept.gallery.data.AudienceType;
import com.concept.gallery.data.PhotoAlbum;
import com.concept.gallery.data.PhotoAlbumRepository;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.shared.data.Parent;
import com.concept.shared.data.Student;
import com.concept.shared.data.StudentRepository;
import com.concept.user.CurrentUserService;
import com.concept.user.User;
import com.concept.user.UserRole;

/**
 * Photo albums: who may make one, who may see one, and which children in an
 * audience have no photo consent.
 *
 * <p>The audience is always resolved from the caller's own records -- a pupil's
 * section, a parent's linked children -- and never from anything in a request.
 * Tenant isolation is here and in the repositories, nowhere else.
 *
 * <p>No uploads yet. This is the slice the upload endpoint and screens will
 * sit on; see docs/media-store-status.md.
 */
@Service
public class GalleryService {

    private static final Set<UserRole> STAFF = Set.of(UserRole.ADMIN, UserRole.PRINCIPAL, UserRole.TEACHER);
    private static final int MAX_TITLE = 255;

    private final PhotoAlbumRepository albumRepository;
    private final AlbumPhotoRepository photoRepository;
    private final ClassSectionRepository classSectionRepository;
    private final StudentRepository studentRepository;
    private final CurrentUserService currentUserService;

    public GalleryService(PhotoAlbumRepository albumRepository,
                          AlbumPhotoRepository photoRepository,
                          ClassSectionRepository classSectionRepository,
                          StudentRepository studentRepository,
                          CurrentUserService currentUserService) {
        this.albumRepository = albumRepository;
        this.photoRepository = photoRepository;
        this.classSectionRepository = classSectionRepository;
        this.studentRepository = studentRepository;
        this.currentUserService = currentUserService;
    }

    // ── Staff ────────────────────────────────────────────────────────────────

    /**
     * Creates an album once a member of staff has confirmed photo consent.
     *
     * <p>Who confirmed and when are recorded on the album. The confirmation is
     * required even when every pupil in the audience has consent on record:
     * consent on a child's file says they may be photographed, not that the
     * person uploading has looked.
     */
    @Transactional
    public AlbumSummary createAlbum(CreateAlbum request, Authentication authentication) {
        User staff = requireStaff(authentication);
        UUID tenantId = staff.getTenantId();

        String title = request.title() == null ? "" : request.title().trim();
        if (title.isEmpty()) {
            throw GalleryException.badRequest("Give the album a title.");
        }
        if (title.length() > MAX_TITLE) {
            throw GalleryException.badRequest("Keep the title under " + MAX_TITLE + " characters.");
        }
        Audience audience = resolveAudience(request.audienceType(), request.audienceRef(), tenantId);

        if (!request.consentConfirmed()) {
            long missing = pupilsIn(audience, tenantId).stream().filter(s -> !s.isPhotoConsent()).count();
            throw GalleryException.badRequest(missing == 0
                    ? "Confirm you have checked photo consent before creating the album."
                    : "Confirm you have checked photo consent before creating the album. "
                        + missing + (missing == 1 ? " pupil" : " pupils") + " in "
                        + audience.label() + " have no consent on record.");
        }

        LocalDateTime now = LocalDateTime.now();
        PhotoAlbum album = new PhotoAlbum();
        album.setId(UUID.randomUUID());
        album.setTenantId(tenantId);
        album.setAcademicYearId(staff.getAcademicYearId());
        album.setTitle(title);
        album.setAudienceType(audience.type());
        album.setAudienceRef(audience.ref());
        album.setConsentCheckedByUserId(staff.getId());
        album.setConsentCheckedAt(now);
        album.setCreatedByUserId(staff.getId());
        album.setCreatedAt(now);
        return summarise(albumRepository.save(album), tenantId);
    }

    /**
     * The pupils in an audience with no photo consent on record, so the person
     * creating an album knows whom to leave out before confirming.
     */
    @Transactional(readOnly = true)
    public List<PupilWithoutConsent> pupilsWithoutConsent(AudienceType type, String ref,
                                                          Authentication authentication) {
        User staff = requireStaff(authentication);
        Audience audience = resolveAudience(type, ref, staff.getTenantId());
        return pupilsIn(audience, staff.getTenantId()).stream()
                .filter(s -> !s.isPhotoConsent())
                .sorted(Comparator.comparing(GalleryService::fullName))
                .map(s -> new PupilWithoutConsent(fullName(s), sectionLabel(s.getClassSection())))
                .toList();
    }

    // ── Anyone signed in ─────────────────────────────────────────────────────

    /** The live albums this person may see, newest first. */
    @Transactional(readOnly = true)
    public List<AlbumSummary> albumsFor(Authentication authentication) {
        User user = requireUser(authentication);
        UUID tenantId = user.getTenantId();
        Viewer viewer = viewerFor(user, authentication);
        return albumRepository.liveForTenant(tenantId).stream()
                .filter(viewer::canSee)
                .map(a -> summarise(a, tenantId))
                .toList();
    }

    /**
     * The photos in one album, if this person may see it.
     *
     * <p>An album in another school, or removed, is "not found"; one in this
     * school but for another audience is "forbidden" -- the same split the rest
     * of the app makes.
     */
    @Transactional(readOnly = true)
    public List<PhotoSummary> photosIn(UUID albumId, Authentication authentication) {
        User user = requireUser(authentication);
        UUID tenantId = user.getTenantId();
        PhotoAlbum album = albumRepository.findByIdAndTenantId(albumId, tenantId)
                .filter(a -> a.getRemovedAt() == null)
                .orElseThrow(() -> GalleryException.notFound("That album is not available."));
        if (!viewerFor(user, authentication).canSee(album)) {
            throw GalleryException.forbidden("That album is not shared with you.");
        }
        return photoRepository.liveInAlbum(album.getId(), tenantId).stream()
                .map(p -> new PhotoSummary(p.getId(), p.getWidth(), p.getHeight(), p.getCreatedAt()))
                .toList();
    }

    // ── Audience ─────────────────────────────────────────────────────────────

    /** A validated audience, with words for it. */
    record Audience(AudienceType type, String ref, String label) {
    }

    /**
     * Checks an audience names something in this school, and normalises it.
     *
     * <p>A section is looked up by id within the tenant, so a section id from
     * another school is "not found" rather than silently accepted. A grade must
     * be one this school has a section in.
     */
    private Audience resolveAudience(AudienceType type, String ref, UUID tenantId) {
        if (type == null) {
            throw GalleryException.badRequest("Choose who the album is for.");
        }
        String trimmed = ref == null ? "" : ref.trim();
        switch (type) {
            case SCHOOL:
                return new Audience(AudienceType.SCHOOL, null, "the whole school");
            case GRADE: {
                if (trimmed.isEmpty()) {
                    throw GalleryException.badRequest("Choose a grade.");
                }
                boolean known = classSectionRepository.findByTenantId(tenantId).stream()
                        .anyMatch(cs -> trimmed.equals(cs.getGradeName()));
                if (!known) {
                    throw GalleryException.notFound("This school has no grade called " + trimmed + ".");
                }
                return new Audience(AudienceType.GRADE, trimmed, trimmed);
            }
            case SECTION: {
                UUID sectionId;
                try {
                    sectionId = UUID.fromString(trimmed);
                } catch (IllegalArgumentException e) {
                    throw GalleryException.badRequest("Choose a class.");
                }
                ClassSection section = classSectionRepository.findByIdAndTenantId(sectionId, tenantId)
                        .orElseThrow(() -> GalleryException.notFound("That class is not in this school."));
                return new Audience(AudienceType.SECTION, section.getId().toString(), sectionLabel(section));
            }
            default:
                throw GalleryException.badRequest("Choose who the album is for.");
        }
    }

    private List<Student> pupilsIn(Audience audience, UUID tenantId) {
        return studentRepository.findByTenantId(tenantId).stream()
                .filter(s -> isIn(s, audience.type(), audience.ref()))
                .toList();
    }

    /** Whether a pupil falls inside an album's audience, from their own section. */
    private static boolean isIn(Student pupil, AudienceType type, String ref) {
        ClassSection section = pupil.getClassSection();
        switch (type) {
            case SCHOOL:
                return true;
            case GRADE:
                return section != null && Objects.equals(section.getGradeName(), ref);
            case SECTION:
                return section != null && section.getId().toString().equals(ref);
            default:
                return false;
        }
    }

    // ── Who is looking ───────────────────────────────────────────────────────

    /** What one person may see, settled once per call from their own records. */
    private interface Viewer {
        boolean canSee(PhotoAlbum album);
    }

    private Viewer viewerFor(User user, Authentication authentication) {
        UserRole role = user.getRole();
        if (STAFF.contains(role)) {
            // Staff see every album in their school. Whether a teacher should see
            // only their own classes' albums is a product decision, not taken here.
            return album -> true;
        }
        if (role == UserRole.STUDENT) {
            Optional<Student> self = currentUserService.getCurrentStudent(authentication)
                    .filter(s -> user.getTenantId().equals(s.getTenantId()));
            return album -> self.map(s -> isIn(s, album.getAudienceType(), album.getAudienceRef()))
                    .orElse(false);
        }
        if (role == UserRole.PARENT) {
            Optional<Parent> parent = currentUserService.getCurrentParent(authentication);
            List<Student> children = parent
                    .map(studentRepository::findByParentsContaining)
                    .orElse(List.of()).stream()
                    .filter(s -> user.getTenantId().equals(s.getTenantId()))
                    .toList();
            return album -> children.stream()
                    .anyMatch(c -> isIn(c, album.getAudienceType(), album.getAudienceRef()));
        }
        return album -> false;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private User requireUser(Authentication authentication) {
        return currentUserService.getCurrentUser(authentication)
                .orElseThrow(() -> GalleryException.forbidden("Sign in to see albums."));
    }

    private User requireStaff(Authentication authentication) {
        User user = requireUser(authentication);
        if (!STAFF.contains(user.getRole())) {
            throw GalleryException.forbidden("Only school staff can manage albums.");
        }
        return user;
    }

    private AlbumSummary summarise(PhotoAlbum album, UUID tenantId) {
        return new AlbumSummary(album.getId(), album.getTitle(), album.getAudienceType(),
                labelFor(album, tenantId), album.getCreatedAt());
    }

    private String labelFor(PhotoAlbum album, UUID tenantId) {
        switch (album.getAudienceType()) {
            case SCHOOL:
                return "the whole school";
            case GRADE:
                return album.getAudienceRef();
            case SECTION:
                try {
                    return classSectionRepository
                            .findByIdAndTenantId(UUID.fromString(album.getAudienceRef()), tenantId)
                            .map(GalleryService::sectionLabel)
                            .orElse("a class that no longer exists");
                } catch (IllegalArgumentException e) {
                    return "a class that no longer exists";
                }
            default:
                return "";
        }
    }

    private static String sectionLabel(ClassSection section) {
        if (section == null) {
            return "no class";
        }
        return section.getGradeName() + " " + section.getSectionName();
    }

    private static String fullName(Student s) {
        return ((s.getFirstName() == null ? "" : s.getFirstName()) + " "
                + (s.getLastName() == null ? "" : s.getLastName())).trim();
    }
}

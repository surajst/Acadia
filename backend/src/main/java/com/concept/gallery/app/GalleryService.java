package com.concept.gallery.app;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.concept.assignment.app.TeachingScope;
import com.concept.gallery.app.GalleryViews.AlbumSummary;
import com.concept.gallery.app.GalleryViews.AudienceOption;
import com.concept.gallery.app.GalleryViews.CreateAlbum;
import com.concept.gallery.app.GalleryViews.GalleryStatus;
import com.concept.gallery.app.GalleryViews.PhotoOfPupil;
import com.concept.gallery.app.GalleryViews.PhotoSummary;
import com.concept.gallery.app.GalleryViews.PupilWithoutConsent;
import com.concept.gallery.app.GalleryViews.TaggablePupil;
import com.concept.gallery.data.AlbumPhoto;
import com.concept.gallery.data.AlbumPhotoPupil;
import com.concept.gallery.data.AlbumPhotoPupilRepository;
import com.concept.gallery.data.AlbumPhotoRepository;
import com.concept.gallery.data.AudienceType;
import com.concept.gallery.data.PhotoAlbum;
import com.concept.gallery.data.PhotoAlbumRepository;
import com.concept.media.ImagePipeline;
import com.concept.media.MediaKeys;
import com.concept.media.MediaStore;
import com.concept.media.UnavailableMediaStore;
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
 * <h2>Product rules (decided 2026-10-16)</h2>
 *
 * <ul>
 *   <li><b>Teachers create albums only for sections they teach.</b> Grade and
 *       whole-school albums are for admins and principals.</li>
 *   <li><b>Teachers see</b> whole-school albums, albums for sections they teach
 *       or grades they teach a section in, and albums they made. Admins and
 *       principals see every album in the school.</li>
 *   <li><b>Consent is tracked per child per photo.</b> Staff tag the pupils in a
 *       photo; a pupil without consent cannot be tagged. A photo showing a pupil
 *       whose consent is (later) withdrawn is never sent to pupils or parents --
 *       withdrawal takes effect at once, without deleting anything -- and staff
 *       see it flagged. {@link #photosOfPupil} answers "which photos is my child
 *       in?". Untagged photos rest on the album-level consent confirmation.</li>
 *   <li><b>Upload size:</b> the app-wide 2MB multipart limit stays. Photos are
 *       sent as the raw request body and capped at the pipeline's own limit
 *       instead, which also loosens nothing for bulk import.</li>
 *   <li><b>Who may add and remove photos:</b> admins and principals in any
 *       album; a teacher in albums they made or albums for a class they teach.
 *       Removing a photo deletes the stored images too: these are pictures of
 *       children, and "hidden" is not "gone".</li>
 * </ul>
 */
@Service
public class GalleryService {

    private static final Set<UserRole> STAFF = Set.of(UserRole.ADMIN, UserRole.PRINCIPAL, UserRole.TEACHER);
    private static final int MAX_TITLE = 255;

    /**
     * How long a photo link works. Long enough to scroll an album, short enough
     * that a link copied out of a browser is dead by the time it is shared.
     */
    static final Duration LINK_LIFETIME = Duration.ofMinutes(15);

    private final PhotoAlbumRepository albumRepository;
    private final AlbumPhotoRepository photoRepository;
    private final AlbumPhotoPupilRepository tagRepository;
    private final ClassSectionRepository classSectionRepository;
    private final StudentRepository studentRepository;
    private final CurrentUserService currentUserService;
    private final TeachingScope teachingScope;
    private final MediaStore mediaStore;
    private final TransactionTemplate transactions;

    public GalleryService(PhotoAlbumRepository albumRepository,
                          AlbumPhotoRepository photoRepository,
                          AlbumPhotoPupilRepository tagRepository,
                          ClassSectionRepository classSectionRepository,
                          StudentRepository studentRepository,
                          CurrentUserService currentUserService,
                          TeachingScope teachingScope,
                          MediaStore mediaStore,
                          PlatformTransactionManager transactionManager) {
        this.albumRepository = albumRepository;
        this.photoRepository = photoRepository;
        this.tagRepository = tagRepository;
        this.classSectionRepository = classSectionRepository;
        this.studentRepository = studentRepository;
        this.currentUserService = currentUserService;
        this.teachingScope = teachingScope;
        this.mediaStore = mediaStore;
        this.transactions = new TransactionTemplate(transactionManager);
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
        requireMayAddress(staff, audience, tenantId);

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
        PhotoAlbum saved = albumRepository.save(album);
        return new AlbumSummary(saved.getId(), saved.getTitle(), saved.getAudienceType(),
                labelFor(saved, tenantId), saved.getCreatedAt(), 0, null, true);
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
        requireMayAddress(staff, audience, staff.getTenantId());
        return pupilsIn(audience, staff.getTenantId()).stream()
                .filter(s -> !s.isPhotoConsent())
                .sorted(Comparator.comparing(GalleryService::fullName))
                .map(s -> new PupilWithoutConsent(fullName(s), sectionLabel(s.getClassSection())))
                .toList();
    }

    /**
     * Whom this person may make an album for: a teacher, the classes they teach;
     * an admin or principal, every class, every grade and the whole school.
     */
    @Transactional(readOnly = true)
    public List<AudienceOption> audiencesFor(Authentication authentication) {
        User staff = requireStaff(authentication);
        List<ClassSection> sections = classSectionRepository.findByTenantId(staff.getTenantId()).stream()
                .filter(cs -> teachingScope.isUnrestricted(staff) || teachingScope.teachesSection(staff, cs))
                .sorted(Comparator.comparing(GalleryService::sectionLabel))
                .toList();
        List<AudienceOption> options = new java.util.ArrayList<>();
        if (teachingScope.isUnrestricted(staff)) {
            options.add(new AudienceOption(AudienceType.SCHOOL.name(), null, "The whole school"));
            sections.stream().map(ClassSection::getGradeName).filter(Objects::nonNull).distinct().sorted()
                    .forEach(g -> options.add(new AudienceOption(AudienceType.GRADE.name(), g, "All of " + g)));
        }
        sections.forEach(cs -> options.add(
                new AudienceOption(AudienceType.SECTION.name(), cs.getId().toString(), sectionLabel(cs))));
        return options;
    }

    /** As {@link #pupilsWithoutConsent(AudienceType, String, Authentication)}, from a request's words. */
    @Transactional(readOnly = true)
    public List<PupilWithoutConsent> pupilsWithoutConsent(String type, String ref, Authentication authentication) {
        AudienceType parsed;
        try {
            parsed = type == null ? null : AudienceType.valueOf(type.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            parsed = null;
        }
        return pupilsWithoutConsent(parsed, ref, authentication);
    }

    // ── Anyone signed in ─────────────────────────────────────────────────────

    /** The live albums this person may see, newest first. */
    @Transactional(readOnly = true)
    public List<AlbumSummary> albumsFor(Authentication authentication) {
        User user = requireUser(authentication);
        UUID tenantId = user.getTenantId();
        Viewer viewer = viewerFor(user, authentication);
        boolean staff = STAFF.contains(user.getRole());
        return albumRepository.liveForTenant(tenantId).stream()
                .filter(viewer::canSee)
                .map(a -> {
                    List<Shown> shown = shownIn(a, tenantId, staff);
                    String cover = shown.isEmpty() ? null
                            : link(shown.get(shown.size() - 1).photo().getThumbKey());
                    return new AlbumSummary(a.getId(), a.getTitle(), a.getAudienceType(),
                            labelFor(a, tenantId), a.getCreatedAt(), shown.size(), cover,
                            staff && mayManage(user, a, authentication));
                })
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
        boolean staff = STAFF.contains(user.getRole());
        List<Shown> shown = shownIn(album, tenantId, staff);
        java.util.Map<UUID, List<String>> names = staff ? taggedNames(shown, tenantId) : java.util.Map.of();
        return shown.stream()
                .map(s -> new PhotoSummary(s.photo().getId(), s.photo().getWidth(), s.photo().getHeight(),
                        s.photo().getCreatedAt(), s.withheld(),
                        link(s.photo().getThumbKey()), link(s.photo().getDisplayKey()),
                        names.getOrDefault(s.photo().getId(), List.of())))
                .toList();
    }

    /** A live photo this person may be shown, and whether it is withheld for consent. */
    private record Shown(AlbumPhoto photo, boolean withheld) {
    }

    /**
     * The live photos of an album that this person may be sent, oldest first.
     * Withheld photos go to staff, flagged, and to nobody else -- never merely
     * flagged for a family: a photo of a child whose parent has said no must not
     * reach another parent's phone.
     */
    private List<Shown> shownIn(PhotoAlbum album, UUID tenantId, boolean staff) {
        List<AlbumPhoto> photos = photoRepository.liveInAlbum(album.getId(), tenantId);
        Set<UUID> withheld = withheldForConsent(photos, tenantId);
        return photos.stream()
                .filter(p -> staff || !withheld.contains(p.getId()))
                .map(p -> new Shown(p, withheld.contains(p.getId())))
                .toList();
    }

    private java.util.Map<UUID, List<String>> taggedNames(List<Shown> shown, UUID tenantId) {
        if (shown.isEmpty()) {
            return java.util.Map.of();
        }
        List<AlbumPhotoPupil> tags = tagRepository.onPhotos(shown.stream().map(s -> s.photo().getId()).toList(), tenantId);
        java.util.Map<UUID, String> nameOf = studentRepository.findByIdInAndTenantId(
                        tags.stream().map(AlbumPhotoPupil::getStudentId).collect(Collectors.toSet()), tenantId)
                .stream().collect(Collectors.toMap(Student::getId, GalleryService::fullName));
        return tags.stream().collect(Collectors.groupingBy(AlbumPhotoPupil::getPhotoId,
                Collectors.mapping(t -> nameOf.getOrDefault(t.getStudentId(), ""),
                        Collectors.collectingAndThen(Collectors.toList(), l -> l.stream().sorted().toList()))));
    }

    /** A short-lived link to one stored image, or null where storage is not set up. */
    private String link(String key) {
        if (!mediaStore.available()) {
            return null;
        }
        return mediaStore.readUrl(key, LINK_LIFETIME).toString();
    }

    // ── Adding and removing photos ───────────────────────────────────────────

    /** Whether photos can be stored on this server at all. */
    public GalleryStatus status() {
        return mediaStore.available()
                ? new GalleryStatus(true, null)
                : new GalleryStatus(false, UnavailableMediaStore.NOT_SET_UP);
    }

    /**
     * Adds one photo to an album.
     *
     * <p>In three steps, deliberately outside one transaction: the decode is the
     * slow, memory-heavy part and must not hold a database connection while it
     * runs. So: check the caller may add to the album; make the two sizes and
     * store them; then write the row. If the row cannot be written, the two
     * stored images are deleted again, so a failure leaves nothing behind.
     */
    public PhotoSummary addPhoto(UUID albumId, byte[] upload, Authentication authentication) {
        if (!mediaStore.available()) {
            throw GalleryException.unavailable(UnavailableMediaStore.NOT_SET_UP);
        }
        record Target(User staff, PhotoAlbum album) {
        }
        Target target = transactions.execute(status -> {
            User staff = requireStaff(authentication);
            PhotoAlbum album = requireLiveAlbum(albumId, staff.getTenantId());
            requireMayManage(staff, album, authentication);
            return new Target(staff, album);
        });
        if (upload == null || upload.length == 0) {
            throw GalleryException.badRequest("Choose a photo to upload.");
        }
        if (upload.length > ImagePipeline.MAX_UPLOAD_BYTES) {
            throw uploadTooLarge();
        }

        ImagePipeline.Gallery images;
        try {
            images = ImagePipeline.renderForGallery(upload);
        } catch (IOException e) {
            throw ImagePipeline.BUSY.equals(e.getMessage())
                    ? GalleryException.unavailable(e.getMessage())
                    : GalleryException.badRequest(friendly(e.getMessage()));
        }

        UUID tenantId = target.staff().getTenantId();
        UUID photoId = UUID.randomUUID();
        String thumbKey = MediaKeys.albumThumb(tenantId, target.album().getId(), photoId);
        String displayKey = MediaKeys.albumDisplay(tenantId, target.album().getId(), photoId);
        try {
            mediaStore.put(displayKey, images.display().bytes(), "image/jpeg");
            mediaStore.put(thumbKey, images.thumb().bytes(), "image/jpeg");
            AlbumPhoto saved = transactions.execute(status -> {
                AlbumPhoto photo = new AlbumPhoto();
                photo.setId(photoId);
                photo.setAlbumId(target.album().getId());
                photo.setThumbKey(thumbKey);
                photo.setDisplayKey(displayKey);
                photo.setWidth(images.display().width());
                photo.setHeight(images.display().height());
                photo.setUploadedByUserId(target.staff().getId());
                photo.setCreatedAt(LocalDateTime.now());
                return photoRepository.save(photo);
            });
            return new PhotoSummary(saved.getId(), saved.getWidth(), saved.getHeight(), saved.getCreatedAt(),
                    false, link(thumbKey), link(displayKey), List.of());
        } catch (RuntimeException e) {
            deleteQuietly(displayKey);
            deleteQuietly(thumbKey);
            throw e;
        }
    }

    /**
     * Removes a photo: the row is kept, marked removed, for the consent record;
     * the stored images are deleted.
     */
    @Transactional
    public void removePhoto(UUID photoId, Authentication authentication) {
        User staff = requireStaff(authentication);
        UUID tenantId = staff.getTenantId();
        AlbumPhoto photo = photoRepository.liveByIdInTenant(photoId, tenantId)
                .orElseThrow(() -> GalleryException.notFound("That photo is not available."));
        PhotoAlbum album = requireLiveAlbum(photo.getAlbumId(), tenantId);
        requireMayManage(staff, album, authentication);
        photo.setRemovedAt(LocalDateTime.now());
        photoRepository.save(photo);
        mediaStore.delete(photo.getDisplayKey());
        mediaStore.delete(photo.getThumbKey());
    }

    /** Removes an album and every photo in it, deleting their stored images. */
    @Transactional
    public void removeAlbum(UUID albumId, Authentication authentication) {
        User staff = requireStaff(authentication);
        UUID tenantId = staff.getTenantId();
        PhotoAlbum album = requireLiveAlbum(albumId, tenantId);
        requireMayManage(staff, album, authentication);
        LocalDateTime now = LocalDateTime.now();
        for (AlbumPhoto photo : photoRepository.liveInAlbum(album.getId(), tenantId)) {
            photo.setRemovedAt(now);
            photoRepository.save(photo);
            mediaStore.delete(photo.getDisplayKey());
            mediaStore.delete(photo.getThumbKey());
        }
        album.setRemovedAt(now);
        albumRepository.save(album);
    }

    /** The pupils who may be tagged in this album's photos: in its audience, with consent. */
    @Transactional(readOnly = true)
    public List<TaggablePupil> taggablePupils(UUID albumId, Authentication authentication) {
        User staff = requireStaff(authentication);
        UUID tenantId = staff.getTenantId();
        PhotoAlbum album = requireLiveAlbum(albumId, tenantId);
        requireMayManage(staff, album, authentication);
        return studentRepository.findByTenantId(tenantId).stream()
                .filter(s -> isIn(s, album.getAudienceType(), album.getAudienceRef()))
                .filter(Student::isPhotoConsent)
                .sorted(Comparator.comparing(GalleryService::fullName))
                .map(s -> new TaggablePupil(s.getId(), fullName(s), sectionLabel(s.getClassSection())))
                .toList();
    }

    private PhotoAlbum requireLiveAlbum(UUID albumId, UUID tenantId) {
        return albumRepository.findByIdAndTenantId(albumId, tenantId)
                .filter(a -> a.getRemovedAt() == null)
                .orElseThrow(() -> GalleryException.notFound("That album is not available."));
    }

    /**
     * Admins and principals manage any album; a teacher, albums they made or
     * albums for a class they teach. Seeing a grade or whole-school album is not
     * enough to add to it.
     */
    private boolean mayManage(User staff, PhotoAlbum album, Authentication authentication) {
        if (teachingScope.isUnrestricted(staff)) {
            return true;
        }
        if (staff.getRole() != UserRole.TEACHER || !viewerFor(staff, authentication).canSee(album)) {
            return false;
        }
        if (staff.getId().equals(album.getCreatedByUserId())) {
            return true;
        }
        if (album.getAudienceType() != AudienceType.SECTION) {
            return false;
        }
        try {
            return classSectionRepository.findByIdAndTenantId(UUID.fromString(album.getAudienceRef()), staff.getTenantId())
                    .map(cs -> teachingScope.teachesSection(staff, cs))
                    .orElse(false);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void requireMayManage(User staff, PhotoAlbum album, Authentication authentication) {
        if (!mayManage(staff, album, authentication)) {
            throw GalleryException.forbidden("You can add and remove photos only in albums for classes you teach.");
        }
    }

    private void deleteQuietly(String key) {
        try {
            mediaStore.delete(key);
        } catch (RuntimeException ignored) {
            // Best effort: the upload has already failed, and that is the error to report.
        }
    }

    /** The refusal for a photo over the upload cap (413). */
    public static GalleryException uploadTooLarge() {
        return GalleryException.tooLarge("That photo is larger than "
                + (ImagePipeline.MAX_UPLOAD_BYTES / (1024 * 1024)) + " MB. Try a smaller one.");
    }

    /** The pipeline's reasons, in words a teacher can act on. */
    private static String friendly(String reason) {
        if (reason == null || ImagePipeline.NOT_A_PHOTO.equals(reason)) {
            return ImagePipeline.NOT_A_PHOTO + ". iPhone photos: choose \"Most Compatible\" in Camera settings, or share as JPEG.";
        }
        if (reason.contains("too many pixels") || reason.contains("too large")) {
            return "That photo is too large to process. Try a smaller one.";
        }
        if (reason.contains("not an image this server can read")) {
            return "That file could not be read as a photo. Try saving it as a JPEG first.";
        }
        return reason;
    }

    // ── Who is in a photo ────────────────────────────────────────────────────

    /**
     * Records which pupils appear in a photo.
     *
     * <p>Refused, naming the child, if any of them has no photo consent: the photo
     * should not be in the album. Every pupil must be in the album's audience and
     * this school. Tagging someone already tagged is a no-op, so a retry is safe.
     */
    @Transactional
    public void tagPupils(UUID photoId, Collection<UUID> studentIds, Authentication authentication) {
        User staff = requireStaff(authentication);
        UUID tenantId = staff.getTenantId();
        AlbumPhoto photo = photoRepository.liveByIdInTenant(photoId, tenantId)
                .orElseThrow(() -> GalleryException.notFound("That photo is not available."));
        PhotoAlbum album = albumRepository.findByIdAndTenantId(photo.getAlbumId(), tenantId)
                .orElseThrow(() -> GalleryException.notFound("That photo is not available."));
        if (!viewerFor(staff, authentication).canSee(album)) {
            throw GalleryException.forbidden("That album is not one of yours.");
        }
        if (studentIds == null || studentIds.isEmpty()) {
            return;
        }

        Set<UUID> wanted = new HashSet<>(studentIds);
        List<Student> pupils = studentRepository.findByIdInAndTenantId(wanted, tenantId);
        if (pupils.size() != wanted.size()) {
            throw GalleryException.notFound("One of those pupils is not in this school.");
        }
        for (Student pupil : pupils) {
            if (!isIn(pupil, album.getAudienceType(), album.getAudienceRef())) {
                throw GalleryException.badRequest(fullName(pupil) + " is not in "
                        + labelFor(album, tenantId) + ", so cannot be in this album.");
            }
            if (!pupil.isPhotoConsent()) {
                throw GalleryException.badRequest(fullName(pupil)
                        + " has no photo consent on record, so this photo should not be shared."
                        + " Remove it, or ask the school office to update the consent first.");
            }
        }

        Set<UUID> already = tagRepository.onPhotos(List.of(photo.getId()), tenantId).stream()
                .map(AlbumPhotoPupil::getStudentId)
                .collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now();
        for (Student pupil : pupils) {
            if (already.contains(pupil.getId())) {
                continue;
            }
            AlbumPhotoPupil tag = new AlbumPhotoPupil();
            tag.setId(UUID.randomUUID());
            tag.setPhotoId(photo.getId());
            tag.setStudentId(pupil.getId());
            tag.setTaggedByUserId(staff.getId());
            tag.setTaggedAt(now);
            tagRepository.save(tag);
        }
    }

    /**
     * Every live photo a pupil is tagged in -- the answer a school owes a parent
     * who withdraws consent. Admins and principals, or a teacher of the pupil's
     * section.
     */
    @Transactional(readOnly = true)
    public List<PhotoOfPupil> photosOfPupil(UUID studentId, Authentication authentication) {
        User staff = requireStaff(authentication);
        UUID tenantId = staff.getTenantId();
        Student pupil = studentRepository.findByIdAndTenantId(studentId, tenantId)
                .orElseThrow(() -> GalleryException.notFound("That pupil is not in this school."));
        if (!teachingScope.isUnrestricted(staff)
                && !teachingScope.teachesSection(staff, pupil.getClassSection())) {
            throw GalleryException.forbidden("You do not teach this pupil's class.");
        }
        return tagRepository.photosOf(pupil.getId(), tenantId).stream()
                .map(p -> {
                    String title = albumRepository.findByIdAndTenantId(p.getAlbumId(), tenantId)
                            .map(PhotoAlbum::getTitle).orElse("");
                    return new PhotoOfPupil(p.getId(), p.getAlbumId(), title, p.getCreatedAt());
                })
                .toList();
    }

    /** The photos among these that show a pupil with no photo consent now. */
    private Set<UUID> withheldForConsent(List<AlbumPhoto> photos, UUID tenantId) {
        if (photos.isEmpty()) {
            return Set.of();
        }
        List<AlbumPhotoPupil> tags = tagRepository.onPhotos(
                photos.stream().map(AlbumPhoto::getId).toList(), tenantId);
        if (tags.isEmpty()) {
            return Set.of();
        }
        Set<UUID> withoutConsent = studentRepository.findByIdInAndTenantId(
                        tags.stream().map(AlbumPhotoPupil::getStudentId).collect(Collectors.toSet()), tenantId)
                .stream()
                .filter(s -> !s.isPhotoConsent())
                .map(Student::getId)
                .collect(Collectors.toSet());
        return tags.stream()
                .filter(t -> withoutConsent.contains(t.getStudentId()))
                .map(AlbumPhotoPupil::getPhotoId)
                .collect(Collectors.toSet());
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
        if (teachingScope.isUnrestricted(user)) {
            return album -> true;
        }
        if (role == UserRole.TEACHER) {
            // The sections this teacher takes, and the grades those sit in,
            // settled once for the call.
            List<ClassSection> taught = classSectionRepository.findByTenantId(user.getTenantId()).stream()
                    .filter(cs -> teachingScope.teachesSection(user, cs))
                    .toList();
            Set<String> sectionIds = taught.stream().map(cs -> cs.getId().toString()).collect(Collectors.toSet());
            Set<String> grades = taught.stream().map(ClassSection::getGradeName)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            return album -> user.getId().equals(album.getCreatedByUserId())
                    || album.getAudienceType() == AudienceType.SCHOOL
                    || (album.getAudienceType() == AudienceType.GRADE && grades.contains(album.getAudienceRef()))
                    || (album.getAudienceType() == AudienceType.SECTION && sectionIds.contains(album.getAudienceRef()));
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

    /**
     * Teachers may address only sections they teach; grade and whole-school
     * albums are for admins and principals.
     */
    private void requireMayAddress(User staff, Audience audience, UUID tenantId) {
        if (teachingScope.isUnrestricted(staff)) {
            return;
        }
        if (audience.type() != AudienceType.SECTION) {
            throw GalleryException.forbidden(
                    "Teachers can make albums for classes they teach. Ask an admin for a grade or whole-school album.");
        }
        ClassSection section = classSectionRepository
                .findByIdAndTenantId(UUID.fromString(audience.ref()), tenantId)
                .orElseThrow(() -> GalleryException.notFound("That class is not in this school."));
        if (!teachingScope.teachesSection(staff, section)) {
            throw GalleryException.forbidden("You do not teach " + sectionLabel(section) + ".");
        }
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

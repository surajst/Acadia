package com.concept.gallery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.concept.academics.data.Subject;
import com.concept.academics.data.SubjectRepository;
import com.concept.assignment.app.SubjectAssignmentService;
import com.concept.gallery.app.GalleryException;
import com.concept.gallery.app.GalleryService;
import com.concept.gallery.app.GalleryViews.AlbumSummary;
import com.concept.gallery.app.GalleryViews.CreateAlbum;
import com.concept.gallery.app.GalleryViews.PhotoOfPupil;
import com.concept.gallery.app.GalleryViews.PhotoSummary;
import com.concept.gallery.app.GalleryViews.PupilWithoutConsent;
import com.concept.gallery.data.AlbumPhoto;
import com.concept.gallery.data.AlbumPhotoRepository;
import com.concept.gallery.data.AudienceType;
import com.concept.gallery.data.PhotoAlbum;
import com.concept.gallery.data.PhotoAlbumRepository;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.shared.data.Parent;
import com.concept.shared.data.ParentRepository;
import com.concept.shared.data.Student;
import com.concept.shared.data.StudentRepository;
import com.concept.tenant.AcademicYear;
import com.concept.tenant.AcademicYearRepository;
import com.concept.tenant.Tenant;
import com.concept.tenant.TenantRepository;
import com.concept.user.User;
import com.concept.user.UserRepository;
import com.concept.user.UserRole;

/**
 * Who may make an album, who may see one, and which children have no photo
 * consent.
 *
 * <p>Two sections of one grade, one pupil each: Aarav in 6-A has no photo
 * consent, Diya in 6-B has it. Each has a parent. A second school has its own
 * admin and a section of its own, to prove nothing crosses.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class GalleryServiceTest {

    @Autowired private GalleryService gallery;
    @Autowired private PhotoAlbumRepository albumRepository;
    @Autowired private AlbumPhotoRepository photoRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ParentRepository parentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private SubjectAssignmentService assignmentService;

    private String suffix;
    private UUID tenantId;
    private UUID yearId;
    private ClassSection sixA;
    private ClassSection sixB;
    private ClassSection elsewhere;   // another school's section
    private Authentication admin;
    private Authentication teacher;   // teaches 6-A Mathematics only
    private Student aaravPupil;
    private Student diyaPupil;
    private Authentication aarav;     // 6-A, no consent
    private Authentication diya;      // 6-B, consent
    private Authentication aaravParent;
    private Authentication diyaParent;
    private Authentication otherSchoolAdmin;

    @BeforeEach
    void setup() {
        suffix = UUID.randomUUID().toString().substring(0, 8);

        UUID otherTenant = school("other");
        UUID otherYear = yearId;
        elsewhere = section(otherTenant, otherYear, "Grade 6", "A");
        otherSchoolAdmin = auth(person(otherTenant, otherYear, "other-admin", UserRole.ADMIN).getEmail(), "ADMIN");

        tenantId = school("gal");
        sixA = section(tenantId, yearId, "Grade 6", "A");
        sixB = section(tenantId, yearId, "Grade 6", "B");

        admin = auth(person(tenantId, yearId, "admin", UserRole.ADMIN).getEmail(), "ADMIN");
        User teacherUser = person(tenantId, yearId, "teacher", UserRole.TEACHER);
        catalogueMathematics();
        assignmentService.assignSubject(teacherUser.getId(), sixA.getId(), "Mathematics", true, tenantId);
        teacher = auth(teacherUser.getEmail(), "TEACHER");

        User aaravUser = person(tenantId, yearId, "aarav", UserRole.STUDENT);
        aaravPupil = pupil("Aarav", sixA, aaravUser.getId(), false);
        aarav = auth(aaravUser.getEmail(), "STUDENT");

        User diyaUser = person(tenantId, yearId, "diya", UserRole.STUDENT);
        diyaPupil = pupil("Diya", sixB, diyaUser.getId(), true);
        diya = auth(diyaUser.getEmail(), "STUDENT");

        aaravParent = auth(guardian("rakesh", aaravPupil).getEmail(), "PARENT");
        diyaParent = auth(guardian("meera", diyaPupil).getEmail(), "PARENT");
    }

    // ── Creating: consent ────────────────────────────────────────────────────

    @Test
    void anAlbumRecordsWhoConfirmedConsentAndWhen() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        AlbumSummary created = gallery.createAlbum(
                new CreateAlbum("Sports Day", AudienceType.SECTION, sixA.getId().toString(), true), admin);

        PhotoAlbum stored = albumRepository.findByIdAndTenantId(created.id(), tenantId).orElseThrow();
        User adminUser = userRepository.findByEmail(admin.getName()).orElseThrow();
        assertEquals(adminUser.getId(), stored.getConsentCheckedByUserId());
        assertNotNull(stored.getConsentCheckedAt());
        assertTrue(stored.getConsentCheckedAt().isAfter(before));
        assertEquals("Grade 6 A", created.audienceLabel(), "the label is words, not an id");
    }

    @Test
    void withoutTheConsentConfirmationNothingIsCreatedAndTheGapIsCounted() {
        long before = albumRepository.liveForTenant(tenantId).size();

        GalleryException refused = assertThrows(GalleryException.class, () -> gallery.createAlbum(
                new CreateAlbum("Annual Day", AudienceType.GRADE, "Grade 6", false), admin));

        assertEquals(400, refused.status());
        assertTrue(refused.getMessage().contains("1 pupil in Grade 6 have no consent"), refused.getMessage());
        assertEquals(before, albumRepository.liveForTenant(tenantId).size());
    }

    @Test
    void theGapListNamesOnlyPupilsInTheAudienceWithoutConsent() {
        List<PupilWithoutConsent> gradeGap =
                gallery.pupilsWithoutConsent(AudienceType.GRADE, "Grade 6", admin);
        assertEquals(List.of(new PupilWithoutConsent("Aarav Verma", "Grade 6 A")), gradeGap);

        assertTrue(gallery.pupilsWithoutConsent(AudienceType.SECTION, sixB.getId().toString(), admin)
                .isEmpty(), "everyone in 6-B has consent");
    }

    // ── Creating: who and what ───────────────────────────────────────────────

    @Test
    void pupilsAndParentsCannotCreateAlbumsOrReadTheConsentGap() {
        CreateAlbum request = new CreateAlbum("Mine", AudienceType.SCHOOL, null, true);
        assertEquals(403, assertThrows(GalleryException.class,
                () -> gallery.createAlbum(request, aarav)).status());
        assertEquals(403, assertThrows(GalleryException.class,
                () -> gallery.createAlbum(request, aaravParent)).status());
        assertEquals(403, assertThrows(GalleryException.class,
                () -> gallery.pupilsWithoutConsent(AudienceType.SCHOOL, null, diyaParent)).status());
    }

    @Test
    void anotherSchoolsSectionIsNotFoundRatherThanAccepted() {
        GalleryException refused = assertThrows(GalleryException.class, () -> gallery.createAlbum(
                new CreateAlbum("Trip", AudienceType.SECTION, elsewhere.getId().toString(), true), admin));
        assertEquals(404, refused.status());
    }

    @Test
    void anAudienceMustNameSomethingThisSchoolHas() {
        assertEquals(404, assertThrows(GalleryException.class, () -> gallery.createAlbum(
                new CreateAlbum("Trip", AudienceType.GRADE, "Grade 9", true), admin)).status());
        assertEquals(400, assertThrows(GalleryException.class, () -> gallery.createAlbum(
                new CreateAlbum("Trip", AudienceType.SECTION, "not-an-id", true), admin)).status());
        assertEquals(400, assertThrows(GalleryException.class, () -> gallery.createAlbum(
                new CreateAlbum("Trip", null, null, true), admin)).status());
        assertEquals(400, assertThrows(GalleryException.class, () -> gallery.createAlbum(
                new CreateAlbum("   ", AudienceType.SCHOOL, null, true), admin)).status());
    }

    @Test
    void aSchoolWideAlbumIgnoresAnyReference() {
        AlbumSummary created = gallery.createAlbum(
                new CreateAlbum("Founders' Day", AudienceType.SCHOOL, sixA.getId().toString(), true), admin);
        PhotoAlbum stored = albumRepository.findByIdAndTenantId(created.id(), tenantId).orElseThrow();
        assertEquals(null, stored.getAudienceRef());
    }

    // ── Seeing ───────────────────────────────────────────────────────────────

    @Test
    void aSectionAlbumReachesThatSectionsPupilAndParentOnly() {
        UUID album = gallery.createAlbum(
                new CreateAlbum("6-A Picnic", AudienceType.SECTION, sixA.getId().toString(), true), admin).id();

        assertTrue(ids(gallery.albumsFor(aarav)).contains(album));
        assertTrue(ids(gallery.albumsFor(aaravParent)).contains(album));
        assertTrue(!ids(gallery.albumsFor(diya)).contains(album));
        assertTrue(!ids(gallery.albumsFor(diyaParent)).contains(album));
        assertTrue(ids(gallery.albumsFor(teacher)).contains(album), "staff see the school's albums");
    }

    @Test
    void gradeAndSchoolAlbumsReachEveryoneInThem() {
        UUID grade = gallery.createAlbum(new CreateAlbum("Grade 6 Play", AudienceType.GRADE, "Grade 6", true), admin).id();
        UUID school = gallery.createAlbum(new CreateAlbum("Sports Day", AudienceType.SCHOOL, null, true), admin).id();

        for (Authentication who : List.of(aarav, diya, aaravParent, diyaParent)) {
            List<UUID> seen = ids(gallery.albumsFor(who));
            assertTrue(seen.contains(grade) && seen.contains(school), who.getName());
        }
    }

    @Test
    void anotherSchoolSeesNoneOfThisSchoolsAlbums() {
        UUID album = gallery.createAlbum(new CreateAlbum("Sports Day", AudienceType.SCHOOL, null, true), admin).id();

        assertTrue(!ids(gallery.albumsFor(otherSchoolAdmin)).contains(album));
        assertEquals(404, assertThrows(GalleryException.class,
                () -> gallery.photosIn(album, otherSchoolAdmin)).status(),
                "another school's album is not found, not forbidden");
    }

    @Test
    void photosAreListedOnlyForSomeoneTheAlbumIsFor() {
        UUID album = gallery.createAlbum(
                new CreateAlbum("6-A Picnic", AudienceType.SECTION, sixA.getId().toString(), true), admin).id();
        photo(album, null);
        photo(album, LocalDateTime.now());   // removed

        List<PhotoSummary> seen = gallery.photosIn(album, aaravParent);
        assertEquals(1, seen.size(), "a removed photo is not listed");

        assertEquals(403, assertThrows(GalleryException.class,
                () -> gallery.photosIn(album, diya)).status());
    }

    @Test
    void aRemovedAlbumDisappears() {
        UUID album = gallery.createAlbum(new CreateAlbum("Old", AudienceType.SCHOOL, null, true), admin).id();
        PhotoAlbum stored = albumRepository.findByIdAndTenantId(album, tenantId).orElseThrow();
        stored.setRemovedAt(LocalDateTime.now());
        albumRepository.saveAndFlush(stored);

        assertTrue(!ids(gallery.albumsFor(aarav)).contains(album));
        assertEquals(404, assertThrows(GalleryException.class, () -> gallery.photosIn(album, admin)).status());
    }

    // ── Teachers: what they may make and see ─────────────────────────────────

    @Test
    void aTeacherMakesAlbumsOnlyForSectionsTheyTeach() {
        assertNotNull(gallery.createAlbum(
                new CreateAlbum("6-A Maths Fair", AudienceType.SECTION, sixA.getId().toString(), true), teacher));

        assertEquals(403, assertThrows(GalleryException.class, () -> gallery.createAlbum(
                new CreateAlbum("6-B Trip", AudienceType.SECTION, sixB.getId().toString(), true), teacher)).status());
        assertEquals(403, assertThrows(GalleryException.class, () -> gallery.createAlbum(
                new CreateAlbum("Grade 6 Play", AudienceType.GRADE, "Grade 6", true), teacher)).status());
        assertEquals(403, assertThrows(GalleryException.class, () -> gallery.createAlbum(
                new CreateAlbum("Sports Day", AudienceType.SCHOOL, null, true), teacher)).status());
        assertEquals(403, assertThrows(GalleryException.class,
                () -> gallery.pupilsWithoutConsent(AudienceType.SECTION, sixB.getId().toString(), teacher)).status(),
                "nor read the consent gap of a class they do not teach");
    }

    @Test
    void aTeacherSeesTheirClassesTheirGradeTheSchoolAndTheirOwn() {
        UUID mine = gallery.createAlbum(
                new CreateAlbum("Mine", AudienceType.SECTION, sixA.getId().toString(), true), teacher).id();
        UUID otherSection = gallery.createAlbum(
                new CreateAlbum("6-B only", AudienceType.SECTION, sixB.getId().toString(), true), admin).id();
        UUID grade = gallery.createAlbum(new CreateAlbum("Grade 6", AudienceType.GRADE, "Grade 6", true), admin).id();
        UUID school = gallery.createAlbum(new CreateAlbum("School", AudienceType.SCHOOL, null, true), admin).id();

        List<UUID> seen = ids(gallery.albumsFor(teacher));
        assertTrue(seen.contains(mine) && seen.contains(grade) && seen.contains(school), seen.toString());
        assertTrue(!seen.contains(otherSection), "a section they do not teach is not theirs to see");
        assertEquals(403, assertThrows(GalleryException.class,
                () -> gallery.photosIn(otherSection, teacher)).status());
        assertTrue(ids(gallery.albumsFor(admin)).contains(otherSection), "admins see everything");
    }

    // ── Who is in a photo ────────────────────────────────────────────────────

    @Test
    void aPupilWithoutConsentCannotBeTaggedAndIsNamed() {
        UUID album = gallery.createAlbum(new CreateAlbum("Sports Day", AudienceType.SCHOOL, null, true), admin).id();
        UUID photo = photo(album, null);

        GalleryException refused = assertThrows(GalleryException.class,
                () -> gallery.tagPupils(photo, List.of(aaravPupil.getId()), admin));
        assertEquals(400, refused.status());
        assertTrue(refused.getMessage().startsWith("Aarav Verma has no photo consent"), refused.getMessage());
    }

    @Test
    void aPupilOutsideTheAlbumsAudienceCannotBeTagged() {
        UUID album = gallery.createAlbum(
                new CreateAlbum("6-A Picnic", AudienceType.SECTION, sixA.getId().toString(), true), admin).id();
        UUID photo = photo(album, null);

        assertEquals(400, assertThrows(GalleryException.class,
                () -> gallery.tagPupils(photo, List.of(diyaPupil.getId()), admin)).status());
    }

    @Test
    void withdrawingConsentHidesThePhotoFromFamiliesAtOnce() {
        UUID album = gallery.createAlbum(new CreateAlbum("Sports Day", AudienceType.SCHOOL, null, true), admin).id();
        UUID withDiya = photo(album, null);
        UUID crowd = photo(album, null);
        gallery.tagPupils(withDiya, List.of(diyaPupil.getId()), admin);
        gallery.tagPupils(withDiya, List.of(diyaPupil.getId()), admin); // a retry is harmless

        assertEquals(2, gallery.photosIn(album, aaravParent).size());

        diyaPupil.setPhotoConsent(false);
        studentRepository.saveAndFlush(diyaPupil);

        for (Authentication family : List.of(aaravParent, diyaParent, aarav, diya)) {
            List<PhotoSummary> seen = gallery.photosIn(album, family);
            assertEquals(List.of(crowd), seen.stream().map(PhotoSummary::id).toList(), family.getName());
        }
        List<PhotoSummary> staffView = gallery.photosIn(album, admin);
        assertEquals(2, staffView.size(), "staff still see it, so they can act on it");
        assertTrue(staffView.stream().filter(p -> p.id().equals(withDiya)).findFirst().orElseThrow()
                .withheldForConsent());
    }

    @Test
    void staffCanListEveryPhotoAPupilIsIn() {
        UUID album = gallery.createAlbum(new CreateAlbum("Sports Day", AudienceType.SCHOOL, null, true), admin).id();
        UUID photo = photo(album, null);
        gallery.tagPupils(photo, List.of(diyaPupil.getId()), admin);

        List<PhotoOfPupil> diyas = gallery.photosOfPupil(diyaPupil.getId(), admin);
        assertEquals(List.of(photo), diyas.stream().map(PhotoOfPupil::photoId).toList());
        assertEquals("Sports Day", diyas.get(0).albumTitle());

        assertEquals(403, assertThrows(GalleryException.class,
                () -> gallery.photosOfPupil(diyaPupil.getId(), teacher)).status(),
                "a teacher of another class may not ask");
        assertEquals(403, assertThrows(GalleryException.class,
                () -> gallery.photosOfPupil(diyaPupil.getId(), diyaParent)).status());
        assertEquals(404, assertThrows(GalleryException.class,
                () -> gallery.photosOfPupil(diyaPupil.getId(), otherSchoolAdmin)).status());
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private static List<UUID> ids(List<AlbumSummary> albums) {
        return albums.stream().map(AlbumSummary::id).toList();
    }

    private UUID school(String prefix) {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Gallery " + prefix);
        tenant.setSubdomain(prefix + "-" + suffix);
        tenant.setActive(true);
        tenant.setCreatedAt(Instant.now());
        UUID id = tenantRepository.saveAndFlush(tenant).getId();

        AcademicYear year = new AcademicYear();
        year.setId(UUID.randomUUID());
        year.setTenantId(id);
        year.setName("2026-27");
        year.setStartDate(LocalDate.now().minusMonths(3));
        year.setEndDate(LocalDate.now().plusMonths(9));
        year.setCurrent(true);
        yearId = academicYearRepository.saveAndFlush(year).getId();
        return id;
    }

    private ClassSection section(UUID tenant, UUID year, String grade, String name) {
        ClassSection cs = new ClassSection();
        cs.setId(UUID.randomUUID());
        cs.setTenantId(tenant);
        cs.setAcademicYearId(year);
        cs.setGradeName(grade);
        cs.setSectionName(name);
        return classSectionRepository.saveAndFlush(cs);
    }

    private User person(UUID tenant, UUID year, String name, UserRole role) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setTenantId(tenant);
        u.setAcademicYearId(year);
        u.setEmail(name + "-" + suffix + "@example.com");
        u.setPasswordHash("irrelevant");
        u.setFullName(name);
        u.setRole(role);
        return userRepository.saveAndFlush(u);
    }

    private Student pupil(String first, ClassSection in, UUID userId, boolean consent) {
        Student s = new Student();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setAcademicYearId(yearId);
        s.setFirstName(first);
        s.setLastName("Verma");
        s.setClassSection(in);
        s.setUserId(userId);
        s.setPhotoConsent(consent);
        return studentRepository.saveAndFlush(s);
    }

    private User guardian(String name, Student child) {
        User u = person(tenantId, yearId, name, UserRole.PARENT);
        Parent p = new Parent();
        p.setId(UUID.randomUUID());
        p.setTenantId(tenantId);
        p.setAcademicYearId(yearId);
        p.setFirstName(name);
        p.setLastName("Verma");
        p.setUserId(u.getId());
        p = parentRepository.saveAndFlush(p);
        child.getParents().add(p);
        studentRepository.saveAndFlush(child);
        return u;
    }

    private void catalogueMathematics() {
        Subject s = new Subject();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setAcademicYearId(yearId);
        s.setCode("MATHEMATICS");
        s.setDisplayName("Mathematics");
        s.setActive(true);
        s.setSortOrder(1);
        subjectRepository.saveAndFlush(s);
    }

    private UUID photo(UUID albumId, LocalDateTime removedAt) {
        AlbumPhoto p = new AlbumPhoto();
        p.setId(UUID.randomUUID());
        p.setAlbumId(albumId);
        p.setThumbKey("tenant/" + tenantId + "/albums/" + albumId + "/" + p.getId() + "-thumb.jpg");
        p.setDisplayKey("tenant/" + tenantId + "/albums/" + albumId + "/" + p.getId() + "-display.jpg");
        p.setWidth(1600);
        p.setHeight(1200);
        p.setUploadedByUserId(userRepository.findByEmail(admin.getName()).orElseThrow().getId());
        p.setRemovedAt(removedAt);
        return photoRepository.save(p).getId();
    }

    private static Authentication auth(String email, String role) {
        return new UsernamePasswordAuthenticationToken(email, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}

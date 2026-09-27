package com.concept.video;

import com.concept.assignment.app.SubjectAssignmentService;
import com.concept.academics.data.Subject;
import com.concept.academics.data.SubjectRepository;
import com.concept.notification.data.Notification;
import com.concept.notification.data.NotificationRepository;
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
import com.concept.video.app.LearningVideoService;
import com.concept.video.app.VideoDetails;
import com.concept.video.app.VideoException;
import com.concept.video.app.VideoLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Learning videos, driven through the service rather than a form.
 *
 * <p>The audience rules are the substance. A video is addressed to one class
 * section, and every question about who may post it, see it or remove it is
 * settled from the caller's own record -- never from a section id in the request,
 * because a section id in a request is not a permission.
 *
 * <p>{@link VideoLookup} is mocked throughout. CI has no business calling
 * youtube.com: a suite that does fails when a runner has no egress, and fails
 * again the day somebody deletes the video it was written against.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class LearningVideoServiceTest {

    private static final String LINK = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
    private static final String VIDEO_ID = "dQw4w9WgXcQ";

    @Autowired private LearningVideoService videoService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ParentRepository parentRepository;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private SubjectAssignmentService assignmentService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private UserRepository userRepository;

    @MockBean private VideoLookup videoLookup;

    private UUID tenantId;
    private UUID yearId;
    private ClassSection sixA;
    private ClassSection sixB;
    private Student aarav;      // 6-A
    private Student diya;       // 6-B
    private Authentication priya;    // teaches 6-A Mathematics
    private Authentication rahul;    // teaches 6-B Science
    private Authentication aaravAuth;
    private Authentication rakesh;   // Aarav's parent
    private Authentication meera;    // Diya's parent

    @BeforeEach
    void setup() {
        Mockito.when(videoLookup.describe(anyString()))
                .thenReturn(new VideoDetails("Photosynthesis in 5 minutes",
                        "https://i.ytimg.com/vi/" + VIDEO_ID + "/hqdefault.jpg"));

        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Demo SSC");
        tenant.setSubdomain("vid-" + suffix);
        tenant.setActive(true);
        tenant.setCreatedAt(Instant.now());
        tenantId = tenantRepository.saveAndFlush(tenant).getId();

        AcademicYear year = new AcademicYear();
        year.setId(UUID.randomUUID());
        year.setTenantId(tenantId);
        year.setName("2026-27");
        year.setStartDate(LocalDate.now().minusMonths(3));
        year.setEndDate(LocalDate.now().plusMonths(9));
        year.setCurrent(true);
        yearId = academicYearRepository.saveAndFlush(year).getId();

        sixA = section("Grade 6", "A");
        sixB = section("Grade 6", "B");
        catalogue("MATHEMATICS", "Mathematics", 1);
        catalogue("SCIENCE", "Science", 2);
        catalogue("ENGLISH", "English", 3);

        User priyaUser = person("priya-" + suffix + "@example.com", "Priya Sharma", UserRole.TEACHER);
        assignmentService.assignSubject(priyaUser.getId(), sixA.getId(), "Mathematics", true, tenantId);
        priya = auth(priyaUser.getEmail(), "TEACHER");

        User rahulUser = person("rahul-" + suffix + "@example.com", "Rahul Nair", UserRole.TEACHER);
        assignmentService.assignSubject(rahulUser.getId(), sixB.getId(), "Science", true, tenantId);
        rahul = auth(rahulUser.getEmail(), "TEACHER");

        User aaravUser = person("aarav-" + suffix + "@example.com", "Aarav Verma", UserRole.STUDENT);
        aarav = pupil("Aarav", sixA, aaravUser.getId());
        aaravAuth = auth(aaravUser.getEmail(), "STUDENT");

        User diyaUser = person("diya-" + suffix + "@example.com", "Diya Rao", UserRole.STUDENT);
        diya = pupil("Diya", sixB, diyaUser.getId());

        rakesh = auth(guardian("Rakesh", "rakesh-" + suffix + "@example.com", aarav).getEmail(), "PARENT");
        meera = auth(guardian("Meera", "meera-" + suffix + "@example.com", diya).getEmail(), "PARENT");
    }

    private ClassSection section(String grade, String name) {
        ClassSection cs = new ClassSection();
        cs.setId(UUID.randomUUID());
        cs.setTenantId(tenantId);
        cs.setAcademicYearId(yearId);
        cs.setGradeName(grade);
        cs.setSectionName(name);
        return classSectionRepository.saveAndFlush(cs);
    }

    private void catalogue(String code, String displayName, int order) {
        Subject s = new Subject();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setAcademicYearId(yearId);
        s.setCode(code);
        s.setDisplayName(displayName);
        s.setActive(true);
        s.setSortOrder(order);
        subjectRepository.saveAndFlush(s);
    }

    private User person(String email, String name, UserRole role) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setTenantId(tenantId);
        u.setAcademicYearId(yearId);
        u.setEmail(email);
        u.setPasswordHash("irrelevant");
        u.setFullName(name);
        u.setRole(role);
        return userRepository.saveAndFlush(u);
    }

    private Student pupil(String first, ClassSection in, UUID userId) {
        Student s = new Student();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setAcademicYearId(yearId);
        s.setFirstName(first);
        s.setLastName("Verma");
        s.setClassSection(in);
        s.setUserId(userId);
        return studentRepository.saveAndFlush(s);
    }

    private User guardian(String name, String email, Student child) {
        User u = person(email, name, UserRole.PARENT);
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

    private Authentication auth(String email, String role) {
        return new UsernamePasswordAuthenticationToken(email, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private LearningVideoService.VideoView_ post(Authentication who, UUID section, String subject) {
        return videoService.post(LINK, section, subject, "Watch before Friday", who);
    }

    private long unreadVideoRowsFor(UUID userId, UUID videoId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId).stream()
                .filter(n -> !n.isRead() && videoId.equals(n.getRelatedEntityId()))
                .count();
    }

    // ── Posting: what is stored ──────────────────────────────────────────────

    @Test
    void aTeacherAddsAVideoForTheirOwnClass() {
        LearningVideoService.VideoView_ added = post(priya, sixA.getId(), "MATHEMATICS");

        assertEquals(VIDEO_ID, added.youtubeId(), "the id, not the pasted link");
        assertEquals("Photosynthesis in 5 minutes", added.title(),
                "the title comes from YouTube, not from the teacher");
        assertNotNull(added.thumbnailUrl());
        assertTrue(added.embedUrl().startsWith("https://www.youtube-nocookie.com/embed/" + VIDEO_ID));
        assertEquals("Grade 6 - A", added.sectionName());
    }

    // ── Posting: who may ─────────────────────────────────────────────────────

    @Test
    void aTeacherCannotAddAVideoToAClassTheyDoNotTeach() {
        VideoException refused = assertThrows(VideoException.class,
                () -> post(priya, sixB.getId(), "MATHEMATICS"));

        assertEquals(403, refused.status());
        assertTrue(refused.getMessage().contains("Grade 6 - B"), refused.getMessage());
    }

    @Test
    void aTeacherCannotAddAVideoUnderASubjectTheyDoNotTeach() {
        VideoException refused = assertThrows(VideoException.class,
                () -> post(priya, sixA.getId(), "ENGLISH"));

        assertEquals(400, refused.status());
        assertTrue(refused.getMessage().contains("not assigned to teach English"), refused.getMessage());
    }

    /** Somebody has to be able to post for a teacher who has left. */
    @Test
    void anAdminIsNotHeldToEitherRule() {
        User admin = person("admin-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                "School Admin", UserRole.ADMIN);

        assertDoesNotThrow(() -> post(auth(admin.getEmail(), "ADMIN"), sixB.getId(), "ENGLISH"));
    }

    // ── Posting: what is refused before anything leaves the building ─────────

    @Test
    void aPlaylistIsRefusedWithoutAskingYouTubeAnything() {
        VideoException refused = assertThrows(VideoException.class,
                () -> videoService.post("https://www.youtube.com/playlist?list=PLabc123",
                        sixA.getId(), "MATHEMATICS", null, priya));

        assertEquals(400, refused.status());
        assertTrue(refused.getMessage().toLowerCase().contains("playlist"), refused.getMessage());
        Mockito.verify(videoLookup, Mockito.never()).describe(anyString());
    }

    /**
     * And the scope check happens before the lookup too, so this endpoint cannot be
     * used to find out which YouTube videos exist by pointing it at somebody else's
     * class.
     */
    @Test
    void aRefusedTeacherNeverReachesTheLookupEither() {
        assertThrows(VideoException.class, () -> post(priya, sixB.getId(), "MATHEMATICS"));

        Mockito.verify(videoLookup, Mockito.never()).describe(anyString());
    }

    @Test
    void aVideoThatWillNotEmbedIsRefusedInItsOwnerSWords() {
        Mockito.when(videoLookup.describe(anyString())).thenThrow(new VideoLookup.Unplayable(
                "This video can't be played inside the app (the owner has turned off embedding)."));

        VideoException refused = assertThrows(VideoException.class,
                () -> post(priya, sixA.getId(), "MATHEMATICS"));

        assertEquals(400, refused.status());
        assertTrue(refused.getMessage().contains("turned off embedding"), refused.getMessage());
    }

    @Test
    void theSameVideoTwiceOnOneClassIsADoubleClick() {
        post(priya, sixA.getId(), "MATHEMATICS");

        VideoException refused = assertThrows(VideoException.class,
                () -> post(priya, sixA.getId(), "MATHEMATICS"));

        assertEquals(409, refused.status());
    }

    // ── Who can see it ───────────────────────────────────────────────────────

    @Test
    void aPupilSeesTheirOwnClassesVideos() {
        post(priya, sixA.getId(), "MATHEMATICS");

        List<LearningVideoService.VideoView_> theirs = videoService.forStudent(null, aaravAuth);

        assertEquals(1, theirs.size());
        assertEquals(VIDEO_ID, theirs.get(0).youtubeId());
        assertFalse(theirs.get(0).watched());
    }

    @Test
    void aPupilDoesNotSeeAnotherClassesVideos() {
        post(rahul, sixB.getId(), "SCIENCE");

        assertTrue(videoService.forStudent(null, aaravAuth).isEmpty(),
                "Aarav is in 6-A; that video was set for 6-B");
    }

    @Test
    void aParentSeesTheirOwnChildsList() {
        post(priya, sixA.getId(), "MATHEMATICS");

        List<LearningVideoService.VideoView_> theirs = videoService.forParent(null, null, rakesh);

        assertEquals(1, theirs.size());
        assertEquals(VIDEO_ID, theirs.get(0).youtubeId());
    }

    /** The reported shape of this class of bug: a 6-B parent reading 6-A. */
    @Test
    void aParentOfASixBChildCannotListSixAVideos() {
        post(priya, sixA.getId(), "MATHEMATICS");

        assertTrue(videoService.forParent(null, null, meera).isEmpty(),
                "Meera's child is in 6-B; 6-A's videos are not hers to read");
    }

    @Test
    void aParentCannotAskAboutSomebodyElsesChild() {
        post(priya, sixA.getId(), "MATHEMATICS");

        VideoException refused = assertThrows(VideoException.class,
                () -> videoService.forParent(aarav.getId(), null, meera));

        assertEquals(403, refused.status());
        assertTrue(refused.getMessage().contains("not your child"), refused.getMessage());
    }

    @Test
    void theSubjectFilterNarrowsWithoutWideningTheAudience() {
        post(priya, sixA.getId(), "MATHEMATICS");

        assertEquals(1, videoService.forStudent("MATHEMATICS", aaravAuth).size());
        assertTrue(videoService.forStudent("SCIENCE", aaravAuth).isEmpty());
        // Written either way round: a code or a display name is the same subject.
        assertEquals(1, videoService.forStudent("Mathematics", aaravAuth).size());
    }

    // ── Watching ─────────────────────────────────────────────────────────────

    @Test
    void finishingAVideoIsRecordedOnceHoweverOftenItIsReported() {
        LearningVideoService.VideoView_ added = post(priya, sixA.getId(), "MATHEMATICS");

        assertEquals(true, videoService.markWatched(added.id(), aaravAuth).get("first"));
        // The player fires ENDED again when a child rewinds and watches the end
        // twice. The database refuses the second row; nothing else has to.
        assertEquals(false, videoService.markWatched(added.id(), aaravAuth).get("first"));

        assertTrue(videoService.forStudent(null, aaravAuth).get(0).watched());
    }

    @Test
    void aPupilCannotMarkAnotherClassesVideoWatched() {
        LearningVideoService.VideoView_ sixBs = post(rahul, sixB.getId(), "SCIENCE");

        VideoException refused = assertThrows(VideoException.class,
                () -> videoService.markWatched(sixBs.id(), aaravAuth));

        assertEquals(403, refused.status());
    }

    // ── Notifications ────────────────────────────────────────────────────────

    @Test
    void addingAVideoTellsThePupilsAndTheirParents() {
        LearningVideoService.VideoView_ added = post(priya, sixA.getId(), "MATHEMATICS");

        assertEquals(1, unreadVideoRowsFor(aarav.getUserId(), added.id()),
                "the pupil it was set for");
        UUID rakeshUserId = userRepository.findByEmail(rakesh.getName()).orElseThrow().getId();
        assertEquals(1, unreadVideoRowsFor(rakeshUserId, added.id()),
                "and their guardian, who is usually the one who makes it happen");
    }

    @Test
    void watchingItStopsTheHomeScreenSayingItIsWaiting() {
        LearningVideoService.VideoView_ added = post(priya, sixA.getId(), "MATHEMATICS");
        assertEquals(1, unreadVideoRowsFor(aarav.getUserId(), added.id()),
                "the row has to be there before its absence means anything");

        videoService.markWatched(added.id(), aaravAuth);

        assertEquals(0, unreadVideoRowsFor(aarav.getUserId(), added.id()));
    }

    // ── Removing ─────────────────────────────────────────────────────────────

    @Test
    void aRemovedVideoLeavesEveryListAndItsNotificationResolves() {
        LearningVideoService.VideoView_ added = post(priya, sixA.getId(), "MATHEMATICS");
        assertEquals(1, videoService.forStudent(null, aaravAuth).size(),
                "it has to be on the list before its absence means anything");
        assertEquals(1, unreadVideoRowsFor(aarav.getUserId(), added.id()));

        videoService.remove(added.id(), priya);

        assertTrue(videoService.forStudent(null, aaravAuth).isEmpty(), "gone from the pupil's list");
        assertTrue(videoService.forParent(null, null, rakesh).isEmpty(), "and the parent's");
        assertTrue(videoService.mine(priya).isEmpty(), "and the teacher's own");
        assertEquals(0, unreadVideoRowsFor(aarav.getUserId(), added.id()),
                "a notification about something that is gone is the R3-P1-3 fault again");
    }

    @Test
    void anotherTeacherCannotRemoveIt() {
        LearningVideoService.VideoView_ added = post(priya, sixA.getId(), "MATHEMATICS");

        VideoException refused = assertThrows(VideoException.class,
                () -> videoService.remove(added.id(), rahul));

        assertEquals(403, refused.status());
        assertEquals(1, videoService.forStudent(null, aaravAuth).size(), "and it is still there");
    }

    @Test
    void anAdminCanClearUpAfterATeacherWhoHasLeft() {
        LearningVideoService.VideoView_ added = post(priya, sixA.getId(), "MATHEMATICS");
        User admin = person("admin2-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                "School Admin", UserRole.ADMIN);

        assertDoesNotThrow(() -> videoService.remove(added.id(), auth(admin.getEmail(), "ADMIN")));
        assertTrue(videoService.forStudent(null, aaravAuth).isEmpty());
    }

    @Test
    void removingTwiceIsNotFound() {
        LearningVideoService.VideoView_ added = post(priya, sixA.getId(), "MATHEMATICS");
        videoService.remove(added.id(), priya);

        assertEquals(404, assertThrows(VideoException.class,
                () -> videoService.remove(added.id(), priya)).status());
    }

    // ── The teacher's own list ───────────────────────────────────────────────

    @Test
    void aTeacherSeesWhatTheyPostedAndHowManyHaveWatched() {
        LearningVideoService.VideoView_ added = post(priya, sixA.getId(), "MATHEMATICS");
        videoService.markWatched(added.id(), aaravAuth);

        List<LearningVideoService.VideoView_> mine = videoService.mine(priya);

        assertEquals(1, mine.size());
        assertEquals(1, mine.get(0).watchedCount());
    }

    @Test
    void aTeacherDoesNotSeeAnotherTeachersVideosInTheirOwnList() {
        post(rahul, sixB.getId(), "SCIENCE");

        assertTrue(videoService.mine(priya).isEmpty());
    }

    @Test
    void anAdminSeesTheWholeSchools() {
        post(priya, sixA.getId(), "MATHEMATICS");
        post(rahul, sixB.getId(), "SCIENCE");
        User admin = person("admin3-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                "School Admin", UserRole.ADMIN);

        assertEquals(2, videoService.mine(auth(admin.getEmail(), "ADMIN")).size());
    }
}

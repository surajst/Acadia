package com.concept.gallery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.concept.academics.data.Subject;
import com.concept.academics.data.SubjectRepository;
import com.concept.assignment.app.SubjectAssignmentService;
import com.concept.media.ImagePipeline;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The gallery over HTTP: upload, see, remove, and the links that serve the
 * images. Runs on the local media store (dev mode), as CI does.
 *
 * <p>One school, Grade 6 A and B. The teacher takes 6-A only. Diya (6-A) has
 * photo consent and a parent; Kabir (6-B) has a parent too.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"app.dev-mode=true", "app.media.local-dir=target/test-media"})
@Transactional
class GalleryApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
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
    private RequestPostProcessor teacher;
    private RequestPostProcessor otherTeacher;
    private RequestPostProcessor diyaParent;
    private RequestPostProcessor kabirParent;
    private Student diya;

    @BeforeEach
    void setup() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Gallery API");
        tenant.setSubdomain("galapi-" + suffix);
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

        sixA = section("A");
        sixB = section("B");
        Subject maths = new Subject();
        maths.setId(UUID.randomUUID());
        maths.setTenantId(tenantId);
        maths.setAcademicYearId(yearId);
        maths.setCode("MATHEMATICS");
        maths.setDisplayName("Mathematics");
        maths.setActive(true);
        maths.setSortOrder(1);
        subjectRepository.saveAndFlush(maths);

        User t = person("teacher", UserRole.TEACHER);
        assignmentService.assignSubject(t.getId(), sixA.getId(), "Mathematics", true, tenantId);
        teacher = user(t.getEmail()).roles("TEACHER");
        User t2 = person("teacher-b", UserRole.TEACHER);
        assignmentService.assignSubject(t2.getId(), sixB.getId(), "Mathematics", true, tenantId);
        otherTeacher = user(t2.getEmail()).roles("TEACHER");

        diya = pupil("Diya", sixA, true);
        Student kabir = pupil("Kabir", sixB, true);
        diyaParent = user(guardian("meera", diya).getEmail()).roles("PARENT");
        kabirParent = user(guardian("arun", kabir).getEmail()).roles("PARENT");
    }

    @Test
    void aTeacherAddsAPhotoAndAParentInTheClassSeesIt() throws Exception {
        String album = sixAAlbum();

        JsonNode added = body(mvc.perform(upload(album, jpeg(3000, 2000)).with(teacher))
                .andExpect(status().isOk()));
        assertThat(added.get("width").asInt()).isEqualTo(ImagePipeline.DISPLAY_EDGE);

        JsonNode photos = body(mvc.perform(get("/api/gallery/albums/" + album + "/photos").with(diyaParent))
                .andExpect(status().isOk()));
        assertThat(photos).hasSize(1);

        // The link serves a JPEG, and the thumbnail is thumbnail-sized.
        byte[] thumb = fetch(photos.get(0).get("thumbUrl").asText());
        BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(thumb));
        assertThat(Math.max(image.getWidth(), image.getHeight())).isEqualTo(ImagePipeline.THUMB_EDGE);

        JsonNode albums = body(mvc.perform(get("/api/gallery/albums").with(diyaParent)).andExpect(status().isOk()));
        assertThat(albums.get(0).get("photoCount").asInt()).isEqualTo(1);
        assertThat(albums.get(0).get("canAddPhotos").asBoolean()).isFalse();
    }

    @Test
    void aParentWhoseChildIsInAnotherClassCannotSeeTheAlbum() throws Exception {
        String album = sixAAlbum();
        mvc.perform(get("/api/gallery/albums/" + album + "/photos").with(kabirParent))
                .andExpect(status().isForbidden());
    }

    @Test
    void aTeacherOfAnotherClassCannotAddToIt() throws Exception {
        String album = sixAAlbum();
        mvc.perform(upload(album, jpeg(400, 300)).with(otherTeacher))
                .andExpect(status().isForbidden());
    }

    @Test
    void somethingThatIsNotAPhotoIsRefusedInWords() throws Exception {
        String album = sixAAlbum();
        mvc.perform(upload(album, "not a photo at all".getBytes()).with(teacher))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.startsWith(ImagePipeline.NOT_A_PHOTO)));

        byte[] heic = new byte[64];
        System.arraycopy("ftypheic".getBytes(), 0, heic, 4, 8);
        mvc.perform(upload(album, heic).with(teacher))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("iPhone")));
    }

    @Test
    void aPhotoOverTheCapIsRefusedWithoutReadingIt() throws Exception {
        String album = sixAAlbum();
        mvc.perform(upload(album, new byte[ImagePipeline.MAX_UPLOAD_BYTES + 1]).with(teacher))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void removingAPhotoDeletesTheImagesItself() throws Exception {
        String album = sixAAlbum();
        String photoId = body(mvc.perform(upload(album, jpeg(800, 600)).with(teacher))).get("id").asText();
        String thumbUrl = body(mvc.perform(get("/api/gallery/albums/" + album + "/photos").with(teacher)))
                .get(0).get("thumbUrl").asText();
        fetch(thumbUrl);

        mvc.perform(delete("/api/gallery/photos/" + photoId).with(teacher)).andExpect(status().isOk());

        assertThat(body(mvc.perform(get("/api/gallery/albums/" + album + "/photos").with(diyaParent)))).isEmpty();
        URI link = URI.create(thumbUrl);
        mvc.perform(get(link.getPath() + "?" + link.getRawQuery())).andExpect(status().isNotFound());
    }

    @Test
    void aLinkWithItsSignatureChangedServesNothing() throws Exception {
        String album = sixAAlbum();
        mvc.perform(upload(album, jpeg(800, 600)).with(teacher)).andExpect(status().isOk());
        URI link = URI.create(body(mvc.perform(get("/api/gallery/albums/" + album + "/photos").with(teacher)))
                .get(0).get("thumbUrl").asText());

        mvc.perform(get(link.getPath() + "?" + link.getRawQuery().replaceAll("sig=[^&]+", "sig=forged")))
                .andExpect(status().isForbidden());
        mvc.perform(get(link.getPath())).andExpect(status().isForbidden());
    }

    @Test
    void staffTagAPupilAndSeeTheName() throws Exception {
        String album = sixAAlbum();
        String photoId = body(mvc.perform(upload(album, jpeg(800, 600)).with(teacher))).get("id").asText();
        JsonNode pupils = body(mvc.perform(get("/api/gallery/albums/" + album + "/pupils").with(teacher)));
        assertThat(pupils).hasSize(1);
        assertThat(pupils.get(0).get("name").asText()).isEqualTo("Diya Verma");

        mvc.perform(post("/api/gallery/photos/" + photoId + "/pupils").with(teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentIds\":[\"" + diya.getId() + "\"]}"))
                .andExpect(status().isOk());

        JsonNode photos = body(mvc.perform(get("/api/gallery/albums/" + album + "/photos").with(teacher)));
        assertThat(photos.get(0).get("taggedNames").get(0).asText()).isEqualTo("Diya Verma");
        JsonNode forParent = body(mvc.perform(get("/api/gallery/albums/" + album + "/photos").with(diyaParent)));
        assertThat(forParent.get(0).get("taggedNames")).isEmpty();
    }

    @Test
    void aTeacherIsOfferedOnlyTheClassesTheyTeach() throws Exception {
        JsonNode options = body(mvc.perform(get("/api/gallery/audiences").with(teacher)).andExpect(status().isOk()));
        assertThat(options).hasSize(1);
        assertThat(options.get(0).get("type").asText()).isEqualTo("SECTION");
        assertThat(options.get(0).get("label").asText()).isEqualTo("Grade 6 A");
        mvc.perform(get("/api/gallery/audiences").with(diyaParent)).andExpect(status().isForbidden());
    }

    @Test
    void theStatusSaysPhotosAreOnInDevMode() throws Exception {
        mvc.perform(get("/api/gallery/status").with(diyaParent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private String sixAAlbum() throws Exception {
        return body(mvc.perform(post("/api/gallery/albums").with(teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Sports day\",\"audienceType\":\"SECTION\",\"audienceRef\":\""
                                + sixA.getId() + "\",\"consentConfirmed\":true}"))
                .andExpect(status().isOk())).get("id").asText();
    }

    private static MockHttpServletRequestBuilder upload(String album, byte[] bytes) {
        return post("/api/gallery/albums/" + album + "/photos")
                .contentType(MediaType.IMAGE_JPEG)
                .content(bytes);
    }

    private byte[] fetch(String url) throws Exception {
        URI link = URI.create(url);
        return mvc.perform(get(link.getPath() + "?" + link.getRawQuery()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    private JsonNode body(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static byte[] jpeg(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    private ClassSection section(String name) {
        ClassSection cs = new ClassSection();
        cs.setId(UUID.randomUUID());
        cs.setTenantId(tenantId);
        cs.setAcademicYearId(yearId);
        cs.setGradeName("Grade 6");
        cs.setSectionName(name);
        return classSectionRepository.saveAndFlush(cs);
    }

    private User person(String name, UserRole role) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setTenantId(tenantId);
        u.setAcademicYearId(yearId);
        u.setEmail(name + "-" + suffix + "@example.com");
        u.setPasswordHash("irrelevant");
        u.setFullName(name);
        u.setRole(role);
        return userRepository.saveAndFlush(u);
    }

    private Student pupil(String first, ClassSection in, boolean consent) {
        Student s = new Student();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setAcademicYearId(yearId);
        s.setFirstName(first);
        s.setLastName("Verma");
        s.setClassSection(in);
        s.setPhotoConsent(consent);
        return studentRepository.saveAndFlush(s);
    }

    private User guardian(String name, Student child) {
        User u = person(name, UserRole.PARENT);
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
}

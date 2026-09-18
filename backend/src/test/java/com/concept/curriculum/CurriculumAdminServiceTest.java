package com.concept.curriculum;

import com.concept.curriculum.app.CurriculumAdminService;
import com.concept.curriculum.app.CurriculumException;
import com.concept.curriculum.app.CurriculumService;
import com.concept.curriculum.data.SyllabusType;
import com.concept.tenant.AcademicYear;
import com.concept.tenant.AcademicYearRepository;
import com.concept.tenant.Tenant;
import com.concept.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The curriculum write path, which did not exist.
 *
 * <p>Nothing in the application could create a topic: the only writer was
 * {@code AcademicDataSeeder}, gated on {@code app.dev-mode=true} and therefore
 * never run in production, while the admin page was read-only and the API
 * exposed GET alone. Every real school's Syllabus tab was permanently empty and
 * no one could do anything about it.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
public class CurriculumAdminServiceTest {

    @Autowired private CurriculumAdminService curriculumAdminService;
    @Autowired private CurriculumService curriculumService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;

    private UUID tenantId;
    private UUID yearId;

    @BeforeEach
    public void setup() {
        tenantId = newSchool();
    }

    private UUID newSchool() {
        UUID id = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setName("Curriculum Tenant");
        tenant.setSubdomain("curric-" + id.toString().substring(0, 8));
        tenant.setActive(true);
        tenant.setCreatedAt(Instant.now());
        tenantRepository.saveAndFlush(tenant);

        yearId = UUID.randomUUID();
        AcademicYear year = new AcademicYear();
        year.setId(yearId);
        year.setTenantId(id);
        year.setName("2026-27");
        year.setStartDate(LocalDate.of(2026, 4, 1));
        year.setEndDate(LocalDate.of(2027, 3, 31));
        year.setCurrent(true);
        academicYearRepository.saveAndFlush(year);
        return id;
    }

    private Map<String, Object> add(String subject, int grade, String topic) {
        return curriculumAdminService.create(tenantId, yearId, subject, grade, topic, null, 50);
    }

    @Test
    public void aTopicAddedByAnAdminReachesTheStudentSyllabusQuery() {
        add("MATHEMATICS", 6, "Fractions and decimals");

        // Read back through the same service the pupil's syllabus screen uses,
        // so this covers the wiring and not just the write.
        var topics = curriculumService.getTopics(tenantId, SyllabusType.CBSE, 6, null);

        assertEquals(1, topics.size(), "the pupil's syllabus must show what the admin entered");
        assertEquals("Fractions and decimals", topics.get(0).getTopicName());
    }

    @Test
    public void topicsAppendInTheOrderTheyWereEntered() {
        add("MATHEMATICS", 6, "First");
        add("MATHEMATICS", 6, "Second");
        add("MATHEMATICS", 6, "Third");

        List<String> names = curriculumService.getTopics(tenantId, SyllabusType.CBSE, 6, "MATHEMATICS")
                .stream().map(c -> c.getTopicName()).toList();

        assertEquals(List.of("First", "Second", "Third"), names,
                "without an explicit order every topic would take order 0 and sort arbitrarily");
    }

    @Test
    public void renamingAndRemovingATopic() {
        UUID id = (UUID) add("SCIENCE", 7, "Photosynthesis").get("id");

        curriculumAdminService.update(id, tenantId, null, null, "Photosynthesis and respiration", null, null);
        assertEquals("Photosynthesis and respiration",
                curriculumService.getTopics(tenantId, SyllabusType.CBSE, 7, null).get(0).getTopicName());

        curriculumAdminService.delete(id, tenantId);
        assertTrue(curriculumService.getTopics(tenantId, SyllabusType.CBSE, 7, null).isEmpty());
    }

    @Test
    public void oneSchoolCannotTouchAnothersSyllabus() {
        UUID id = (UUID) add("MATHEMATICS", 6, "Ours").get("id");
        UUID otherSchool = newSchool();

        assertThrows(CurriculumException.class,
                () -> curriculumAdminService.update(id, otherSchool, null, null, "Hijacked", null, null),
                "a topic id from another school must not be editable");
        assertThrows(CurriculumException.class,
                () -> curriculumAdminService.delete(id, otherSchool),
                "a topic id from another school must not be deletable");
    }

    @Test
    public void badInputIsRefusedRatherThanStored() {
        assertThrows(CurriculumException.class, () -> add("MATHEMATICS", 6, "  "));
        assertThrows(CurriculumException.class, () -> add("  ", 6, "Topic"));
        assertThrows(CurriculumException.class, () -> add("MATHEMATICS", 0, "Topic"));
        assertThrows(CurriculumException.class, () -> add("MATHEMATICS", 13, "Topic"));
    }

    @Test
    public void listIsScopedToTheCallersOwnSchool() {
        add("MATHEMATICS", 6, "Ours");
        UUID otherSchool = newSchool();

        assertTrue(curriculumAdminService.list(otherSchool, null).isEmpty(),
                "a new school starts with an empty syllabus, not its neighbour's");
    }
}

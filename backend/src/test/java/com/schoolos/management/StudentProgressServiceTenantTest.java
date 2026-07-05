package com.schoolos.management;

import com.schoolos.tenant.AcademicYear;
import com.schoolos.tenant.AcademicYearRepository;
import com.schoolos.tenant.Tenant;
import com.schoolos.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Covers the cross-tenant curriculum-mixing fix: getProgressByStudent must
// only return the student's own tenant's curriculum topics, never another
// tenant's syllabus.
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
public class StudentProgressServiceTenantTest {

    @Autowired
    private StudentProgressService studentProgressService;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ClassSectionRepository classSectionRepository;

    @Autowired
    private CurriculumRepository curriculumRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private AcademicYearRepository academicYearRepository;

    private Student studentA;

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

    @BeforeEach
    public void setup() {
        UUID tenantA = makeTenant().getId();
        UUID tenantB = makeTenant().getId();
        UUID academicYearIdA = makeAcademicYear(tenantA);
        UUID academicYearIdB = makeAcademicYear(tenantB);

        ClassSection sectionA = new ClassSection();
        sectionA.setId(UUID.randomUUID());
        sectionA.setTenantId(tenantA);
        sectionA.setAcademicYearId(academicYearIdA);
        sectionA.setGradeName("Grade 1");
        sectionA.setSectionName("A");
        classSectionRepository.saveAndFlush(sectionA);

        studentA = new Student();
        studentA.setId(UUID.randomUUID());
        studentA.setTenantId(tenantA);
        studentA.setAcademicYearId(academicYearIdA);
        studentA.setFirstName("Test");
        studentA.setLastName("Student");
        studentA.setClassSection(sectionA);
        studentRepository.saveAndFlush(studentA);

        Curriculum topicA = new Curriculum();
        topicA.setId(UUID.randomUUID());
        topicA.setTenantId(tenantA);
        topicA.setAcademicYearId(academicYearIdA);
        topicA.setSyllabusType(SyllabusType.CBSE);
        topicA.setStandard(1);
        topicA.setSubjectCode("MATH");
        topicA.setTopicName("Own Tenant Topic");
        topicA.setTopicOrder(1);
        curriculumRepository.saveAndFlush(topicA);

        Curriculum topicB = new Curriculum();
        topicB.setId(UUID.randomUUID());
        topicB.setTenantId(tenantB);
        topicB.setAcademicYearId(academicYearIdB);
        topicB.setSyllabusType(SyllabusType.CBSE);
        topicB.setStandard(1);
        topicB.setSubjectCode("MATH");
        topicB.setTopicName("Other Tenant Topic");
        topicB.setTopicOrder(1);
        curriculumRepository.saveAndFlush(topicB);
    }

    @Test
    public void getProgressByStudent_onlyReturnsOwnTenantTopics() {
        Map<String, SubjectProgressDto> progress = studentProgressService.getProgressByStudent(studentA.getId());

        assertTrue(progress.containsKey("MATH"));
        long topicCount = progress.get("MATH").topics().size();
        assertEquals(1, topicCount);
        assertEquals("Own Tenant Topic", progress.get("MATH").topics().get(0).topicName());
    }
}

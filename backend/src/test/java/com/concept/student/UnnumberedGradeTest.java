package com.concept.student;

import com.concept.curriculum.data.Curriculum;
import com.concept.curriculum.data.CurriculumRepository;
import com.concept.curriculum.data.SyllabusType;
import com.concept.student.app.StudentService;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.shared.data.Student;
import com.concept.shared.data.StudentRepository;
import com.concept.tenant.AcademicYear;
import com.concept.tenant.AcademicYearRepository;
import com.concept.tenant.Tenant;
import com.concept.tenant.TenantRepository;
import com.concept.user.User;
import com.concept.user.UserRepository;
import com.concept.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A child in a class whose name has no number in it -- "Nursery", "LKG",
 * "Prep" -- must not be shown another year's work.
 *
 * <p>The grade parser used to answer 6 when it could read no digits, because
 * the fallback was a catch block. So a four-year-old in Nursery was served the
 * Grade 6 syllabus, and it looked like ordinary content rather than a fault.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
public class UnnumberedGradeTest {

    @Autowired private StudentService studentService;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private CurriculumRepository curriculumRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private UserRepository userRepository;

    private UUID tenantId;
    private UUID yearId;
    private Authentication nurseryChild;
    private Authentication gradeSixChild;

    private Authentication makeStudentIn(String gradeName, String email) {
        ClassSection section = new ClassSection();
        section.setId(UUID.randomUUID());
        section.setTenantId(tenantId);
        section.setAcademicYearId(yearId);
        section.setGradeName(gradeName);
        section.setSectionName("A");
        classSectionRepository.saveAndFlush(section);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash("irrelevant");
        user.setFullName(email);
        user.setRole(UserRole.STUDENT);
        user.setTenantId(tenantId);
        user.setAcademicYearId(yearId);
        userRepository.saveAndFlush(user);

        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setTenantId(tenantId);
        student.setAcademicYearId(yearId);
        student.setFirstName("Small");
        student.setLastName("Person");
        student.setClassSection(section);
        student.setUserId(user.getId());
        studentRepository.saveAndFlush(student);

        return new UsernamePasswordAuthenticationToken(email, null,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
    }

    @BeforeEach
    public void setup() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Mixed School");
        tenant.setSubdomain("mixed-" + UUID.randomUUID());
        tenant.setActive(true);
        tenant.setCreatedAt(Instant.now());
        tenantId = tenantRepository.saveAndFlush(tenant).getId();

        AcademicYear year = new AcademicYear();
        year.setId(UUID.randomUUID());
        year.setTenantId(tenantId);
        year.setName("2026");
        year.setStartDate(LocalDate.of(2026, 1, 1));
        year.setEndDate(LocalDate.of(2026, 12, 31));
        year.setCurrent(true);
        yearId = academicYearRepository.saveAndFlush(year).getId();

        // Grade 6 material exists in this school -- that is the point. A school
        // can run a Nursery alongside numbered grades.
        Curriculum topic = new Curriculum();
        topic.setId(UUID.randomUUID());
        topic.setTenantId(tenantId);
        topic.setAcademicYearId(yearId);
        topic.setSyllabusType(SyllabusType.CBSE);
        topic.setStandard(6);
        topic.setSubjectCode("MATH");
        topic.setTopicName("Algebraic Expressions");
        topic.setTopicOrder(1);
        topic.setXpReward(50);
        curriculumRepository.saveAndFlush(topic);

        nurseryChild = makeStudentIn("Nursery", "nursery.child@mixed.test");
        gradeSixChild = makeStudentIn("Grade 6", "grade6.child@mixed.test");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void aNurseryChildIsNotShownTheGradeSixSyllabus() {
        List<Map<String, Object>> syllabus =
                (List<Map<String, Object>>) studentService.mobileSyllabus(nurseryChild);

        assertTrue(syllabus.isEmpty(),
                "a class with no number in its name must resolve to no syllabus, not Grade 6's: " + syllabus);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void aGradeSixChildStillSeesTheirOwnSyllabus() {
        // The guard must not have been bought by breaking the normal case.
        List<Map<String, Object>> syllabus =
                (List<Map<String, Object>>) studentService.mobileSyllabus(gradeSixChild);

        assertEquals(1, syllabus.size());
        assertEquals("Algebraic Expressions", syllabus.get(0).get("topicName"));
    }
}

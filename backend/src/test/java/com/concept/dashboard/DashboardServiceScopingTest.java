package com.concept.dashboard;

import com.concept.assignment.data.SubjectAssignment;
import com.concept.assignment.data.SubjectAssignmentRepository;
import com.concept.dashboard.app.DashboardService;
import com.concept.dashboard.app.RosterDashboardView;
import com.concept.dashboard.app.StudentRow;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The roster dashboard must show a teacher only the classes they teach.
 *
 * <p>It previously showed them the whole school. The teacher id was invented as
 * {@code UUID.nameUUIDFromBytes(email)} and matched against
 * {@code ClassSection.teacherId}, a column only the dev-mode seeders ever write,
 * so the lookup always came back empty — and an "if empty, use every section in
 * the tenant" fallback then widened the view instead of narrowing it. Each test
 * here pins one of the ways that could be reached: the plain listing, the name
 * search, and a class id typed straight into the query string.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
public class DashboardServiceScopingTest {

    @Autowired private DashboardService dashboardService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SubjectAssignmentRepository subjectAssignmentRepository;

    private UUID tenantId;
    private UUID yearId;
    private ClassSection taught;
    private ClassSection notTaught;
    private User teacher;
    private User admin;

    @BeforeEach
    public void setup() {
        tenantId = UUID.randomUUID();
        yearId = UUID.randomUUID();
        String tag = tenantId.toString().substring(0, 8);

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName("Scope Tenant");
        tenant.setSubdomain("scope-" + tag);
        tenant.setActive(true);
        tenant.setCreatedAt(Instant.now());
        tenantRepository.saveAndFlush(tenant);

        AcademicYear year = new AcademicYear();
        year.setId(yearId);
        year.setTenantId(tenantId);
        year.setName("2026-27");
        year.setStartDate(LocalDate.of(2026, 4, 1));
        year.setEndDate(LocalDate.of(2027, 3, 31));
        year.setCurrent(true);
        academicYearRepository.saveAndFlush(year);

        taught = section("Grade 6", "A");
        notTaught = section("Grade 7", "B");

        teacher = user("teacher-" + tag + "@school.edu", UserRole.TEACHER);
        admin = user("admin-" + tag + "@school.edu", UserRole.ADMIN);

        SubjectAssignment assignment = new SubjectAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setTenantId(tenantId);
        assignment.setAcademicYearId(yearId);
        assignment.setTeacher(teacher);
        assignment.setClassSection(taught);
        assignment.setSubjectName("Mathematics");
        assignment.setHomeClass(true);
        subjectAssignmentRepository.saveAndFlush(assignment);

        student("Mine", taught);
        student("Theirs", notTaught);
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

    private User user(String email, UserRole role) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setTenantId(tenantId);
        u.setAcademicYearId(yearId);
        u.setEmail(email);
        u.setPasswordHash("x");
        u.setFullName(role + " User");
        u.setRole(role);
        u.setActive(true);
        return userRepository.saveAndFlush(u);
    }

    private Student student(String first, ClassSection section) {
        Student s = new Student();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setAcademicYearId(yearId);
        s.setFirstName(first);
        s.setLastName("Pupil");
        s.setRollNumber(first + "-1");
        s.setClassSection(section);
        return studentRepository.saveAndFlush(s);
    }

    private Set<String> firstNames(RosterDashboardView view) {
        return view.roster().stream().map(StudentRow::firstName).collect(Collectors.toSet());
    }

    private RosterDashboardView forUser(User u, UUID classId, String nameFilter) {
        return dashboardService.buildRosterDashboard(tenantId, u.getEmail(), classId,
                nameFilter, null, 0, 20, false);
    }

    @Test
    public void teacherSeesOnlyTheClassesTheyTeach() {
        Set<String> names = firstNames(forUser(teacher, null, null));

        assertTrue(names.contains("Mine"), "teacher should see their own class");
        assertTrue(!names.contains("Theirs"),
                "teacher must not see a section they are not assigned to; got " + names);
    }

    @Test
    public void adminStillSeesTheWholeSchool() {
        Set<String> names = firstNames(forUser(admin, null, null));

        assertEquals(Set.of("Mine", "Theirs"), names,
                "an admin's school-wide view must not be narrowed by this change");
    }

    @Test
    public void nameSearchCannotReachOutsideTheTeachersClasses() {
        // The search branch used to fall through to a tenant-wide query whenever
        // the assigned-section list came back empty, so typing a name was a way
        // around the scope even once the listing was fixed.
        Set<String> names = firstNames(forUser(teacher, null, "Theirs"));

        assertTrue(names.isEmpty(),
                "searching for a pupil in another teacher's class must return nothing; got " + names);
    }

    @Test
    public void classIdFromTheQueryStringCannotReachAnotherClass() {
        Set<String> names = firstNames(forUser(teacher, notTaught.getId(), null));

        assertTrue(names.isEmpty(),
                "a class id the teacher does not teach must return nothing; got " + names);
    }

    @Test
    public void teacherWithNoAssignmentsSeesNobody() {
        // The old fallback turned "assigned to nothing" into "assigned to
        // everything", which is the exact inversion this guards against.
        User unassigned = user("spare-" + tenantId.toString().substring(0, 8) + "@school.edu",
                UserRole.TEACHER);

        Set<String> names = firstNames(forUser(unassigned, null, null));

        assertTrue(names.isEmpty(), "an unassigned teacher must see nobody; got " + names);
    }

    @Test
    public void gradeFilterOffersOnlyTheTeachersOwnGrades() {
        List<String> grades = forUser(teacher, null, null).allGradeNames();

        assertEquals(List.of("Grade 6"), grades,
                "the grade dropdown must not advertise grades the teacher cannot open");
    }
}

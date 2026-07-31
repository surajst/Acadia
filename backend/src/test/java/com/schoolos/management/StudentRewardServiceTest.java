package com.schoolos.management;

import com.schoolos.academics.StudentMetric;
import com.schoolos.academics.StudentMetricRepository;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the IDOR ownership guards and XP arithmetic in
 * {@link StudentRewardService} directly, without the web layer — the reason this
 * logic was lifted out of StudentPortalController.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
public class StudentRewardServiceTest {

    @Autowired private StudentRewardService studentRewardService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private StudentMetricRepository studentMetricRepository;
    @Autowired private ParentQuestRepository parentQuestRepository;
    @Autowired private ParentRewardRepository parentRewardRepository;
    @Autowired private ParentRepository parentRepository;

    private UUID tenantId;
    private UUID yearId;
    private ClassSection classSection;
    private Parent parent;

    @BeforeEach
    public void setup() {
        tenantId = UUID.randomUUID();
        yearId = UUID.randomUUID();

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName("Reward Tenant");
        tenant.setSubdomain("rw-" + tenantId.toString().substring(0, 8));
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

        classSection = new ClassSection();
        classSection.setId(UUID.randomUUID());
        classSection.setTenantId(tenantId);
        classSection.setAcademicYearId(yearId);
        classSection.setGradeName("Grade 5");
        classSection.setSectionName("A");
        classSectionRepository.saveAndFlush(classSection);

        parent = new Parent();
        parent.setId(UUID.randomUUID());
        parent.setTenantId(tenantId);
        parent.setAcademicYearId(yearId);
        parent.setFirstName("Test");
        parent.setLastName("Parent");
        parent.setPhoneNumber("+91 9800000000");
        parentRepository.saveAndFlush(parent);
    }

    private Student newStudent(String name) {
        Student s = new Student();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setAcademicYearId(yearId);
        s.setFirstName(name);
        s.setLastName("Test");
        s.setClassSection(classSection);
        return studentRepository.saveAndFlush(s);
    }

    private StudentMetric metricFor(Student s, int schoolXp, int parentXp) {
        StudentMetric m = new StudentMetric();
        m.setId(UUID.randomUUID());
        m.setStudent(s);
        m.setTenantId(tenantId);
        m.setAcademicYearId(yearId);
        m.setSchoolXp(schoolXp);
        m.setParentXp(parentXp);
        m.setActiveStreak(0);
        return studentMetricRepository.saveAndFlush(m);
    }

    private ParentReward reward(Student owner, int cost) {
        ParentReward r = new ParentReward();
        r.setId(UUID.randomUUID());
        r.setTenantId(tenantId);
        r.setAcademicYearId(yearId);
        r.setStudent(owner);
        r.setParent(parent);
        r.setRewardTitle("Ice cream");
        r.setXpCost(cost);
        r.setStatus("PENDING");
        return parentRewardRepository.saveAndFlush(r);
    }

    private ParentQuest quest(Student owner) {
        ParentQuest q = new ParentQuest();
        q.setId(UUID.randomUUID());
        q.setTenantId(tenantId);
        q.setAcademicYearId(yearId);
        q.setStudent(owner);
        q.setParent(parent);
        q.setTaskDescription("Tidy your room");
        q.setXpBounty(50);
        q.setStatus("PENDING");
        return parentQuestRepository.saveAndFlush(q);
    }

    @Test
    public void claimQuest_rejectsWhenCallerDoesNotOwnQuest() {
        Student owner = newStudent("Owner");
        Student attacker = newStudent("Attacker");
        ParentQuest q = quest(owner);

        assertThrows(IllegalArgumentException.class, () -> studentRewardService.claimQuest(q.getId(), attacker));
        // Status must be untouched.
        assertEquals("PENDING", parentQuestRepository.findById(q.getId()).orElseThrow().getStatus());
    }

    @Test
    public void claimQuest_succeedsForOwner() {
        Student owner = newStudent("Owner");
        ParentQuest q = quest(owner);

        studentRewardService.claimQuest(q.getId(), owner);

        assertEquals("COMPLETED_AWAITING_APPROVAL", parentQuestRepository.findById(q.getId()).orElseThrow().getStatus());
    }

    @Test
    public void redeemParentReward_rejectsWhenCallerDoesNotOwnReward() {
        Student owner = newStudent("Owner");
        Student attacker = newStudent("Attacker");
        metricFor(attacker, 1000, 1000);
        ParentReward r = reward(owner, 100);

        assertThrows(IllegalArgumentException.class, () -> studentRewardService.redeemParentReward(r.getId(), attacker));
        assertEquals("PENDING", parentRewardRepository.findById(r.getId()).orElseThrow().getStatus());
    }

    @Test
    public void redeemParentReward_insufficientXpDoesNotMutate() {
        Student owner = newStudent("Owner");
        metricFor(owner, 30, 20); // 50 total
        ParentReward r = reward(owner, 100);

        StudentRewardService.RedeemOutcome outcome = studentRewardService.redeemParentReward(r.getId(), owner);

        assertEquals(StudentRewardService.RedeemOutcome.INSUFFICIENT_XP, outcome);
        StudentMetric m = studentMetricRepository.findByStudentId(owner.getId()).orElseThrow();
        assertEquals(30, m.getSchoolXp());
        assertEquals(20, m.getParentXp());
        assertEquals("PENDING", parentRewardRepository.findById(r.getId()).orElseThrow().getStatus());
    }

    @Test
    public void redeemParentReward_deductsParentXpFirstThenSchoolXp() {
        Student owner = newStudent("Owner");
        metricFor(owner, 100, 50); // 150 total
        ParentReward r = reward(owner, 120);

        StudentRewardService.RedeemOutcome outcome = studentRewardService.redeemParentReward(r.getId(), owner);

        assertEquals(StudentRewardService.RedeemOutcome.REDEEMED, outcome);
        StudentMetric m = studentMetricRepository.findByStudentId(owner.getId()).orElseThrow();
        assertEquals(0, m.getParentXp());   // 50 parent XP spent first
        assertEquals(30, m.getSchoolXp());  // remaining 70 from school XP -> 100-70
        assertEquals("CLAIMED_AWAITING_DELIVERY", parentRewardRepository.findById(r.getId()).orElseThrow().getStatus());
    }
}

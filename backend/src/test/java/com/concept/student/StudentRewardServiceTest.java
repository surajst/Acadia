package com.concept.student;
import com.concept.student.app.StudentRewardService;
import com.concept.parent.data.ParentRewardRepository;
import com.concept.parent.data.ParentReward;
import com.concept.parent.data.ParentQuestRepository;
import com.concept.parent.data.ParentQuest;
import com.concept.rewards.data.RewardItemRepository;
import com.concept.rewards.data.RewardItem;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ParentRepository;
import com.concept.shared.data.Parent;
import com.concept.shared.data.StudentRepository;
import com.concept.shared.data.Student;

import com.concept.academics.data.StudentMetric;
import com.concept.academics.data.StudentMetricRepository;
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
    @Autowired private RewardItemRepository rewardItemRepository;

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

    private RewardItem rewardItem(int cost) {
        RewardItem item = new RewardItem();
        item.setId(UUID.randomUUID());
        item.setTenantId(tenantId);
        item.setAcademicYearId(yearId);
        item.setTitle("Extra recess");
        item.setXpCost(cost);
        item.setInventoryCount(10);
        return rewardItemRepository.saveAndFlush(item);
    }

    private Student linkParent(Student s) {
        s.getParents().add(parent);
        return studentRepository.saveAndFlush(s);
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
    public void redeemReward_rejectsWhenStudentHasNoLinkedParent() {
        Student student = newStudent("Orphaned");
        metricFor(student, 500, 0);
        RewardItem item = rewardItem(100);

        StudentRewardService.RedeemOutcome outcome = studentRewardService.redeemReward(item.getId(), student);

        assertEquals(StudentRewardService.RedeemOutcome.NO_LINKED_PARENT, outcome);
        // No XP spent and no pending ParentReward created for an unroutable redeem.
        assertEquals(500, studentMetricRepository.findByStudentId(student.getId()).orElseThrow().getSchoolXp());
        assertTrue(parentRewardRepository.findAll().stream()
                .noneMatch(r -> r.getStudent() != null && r.getStudent().getId().equals(student.getId())));
    }

    @Test
    public void redeemReward_succeedsAndQueuesPendingRewardForLinkedParent() {
        Student student = linkParent(newStudent("Linked"));
        metricFor(student, 500, 0);
        RewardItem item = rewardItem(100);

        StudentRewardService.RedeemOutcome outcome = studentRewardService.redeemReward(item.getId(), student);

        assertEquals(StudentRewardService.RedeemOutcome.REDEEMED, outcome);
        assertEquals(400, studentMetricRepository.findByStudentId(student.getId()).orElseThrow().getSchoolXp());
        ParentReward pending = parentRewardRepository.findAll().stream()
                .filter(r -> r.getStudent() != null && r.getStudent().getId().equals(student.getId()))
                .findFirst().orElseThrow();
        assertEquals("PENDING", pending.getStatus());
        assertEquals(parent.getId(), pending.getParent().getId());
    }

    @Test
    public void redeemReward_insufficientXpDoesNotMutate() {
        Student student = linkParent(newStudent("Linked"));
        metricFor(student, 50, 0);
        RewardItem item = rewardItem(100);

        StudentRewardService.RedeemOutcome outcome = studentRewardService.redeemReward(item.getId(), student);

        assertEquals(StudentRewardService.RedeemOutcome.INSUFFICIENT_XP, outcome);
        assertEquals(50, studentMetricRepository.findByStudentId(student.getId()).orElseThrow().getSchoolXp());
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

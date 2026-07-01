package com.schoolos;

import com.schoolos.announcement.Announcement;
import com.schoolos.announcement.AnnouncementService;
import com.schoolos.announcement.AnnouncementRepository;
import com.schoolos.management.ClassSection;
import com.schoolos.management.ClassSectionRepository;
import com.schoolos.management.Student;
import com.schoolos.management.StudentRepository;
import com.schoolos.management.Parent;
import com.schoolos.management.ParentRepository;
import com.schoolos.management.ParentQuest;
import com.schoolos.management.ParentReward;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.UUID;

@Component
public class DemoTestHarness implements CommandLineRunner {

    private final AnnouncementService announcementService;
    private final AnnouncementRepository announcementRepository;
    private final ClassSectionRepository classSectionRepository;
    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final com.schoolos.academics.MathChapterRepository mathChapterRepository;
    private final com.schoolos.academics.MathSkillRepository mathSkillRepository;
    private final com.schoolos.academics.StudentMetricRepository studentMetricRepository;
    private final com.schoolos.management.ParentQuestRepository parentQuestRepository;
    private final com.schoolos.management.ParentRewardRepository parentRewardRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public DemoTestHarness(AnnouncementService announcementService, 
                           AnnouncementRepository announcementRepository, 
                           ClassSectionRepository classSectionRepository,
                           StudentRepository studentRepository,
                           ParentRepository parentRepository,
                           com.schoolos.academics.MathChapterRepository mathChapterRepository,
                           com.schoolos.academics.MathSkillRepository mathSkillRepository,
                           com.schoolos.academics.StudentMetricRepository studentMetricRepository,
                           com.schoolos.management.ParentQuestRepository parentQuestRepository,
                           com.schoolos.management.ParentRewardRepository parentRewardRepository,
                           JdbcTemplate jdbcTemplate,
                           PasswordEncoder passwordEncoder) {
        this.announcementService = announcementService;
        this.announcementRepository = announcementRepository;
        this.classSectionRepository = classSectionRepository;
        this.studentRepository = studentRepository;
        this.parentRepository = parentRepository;
        this.mathChapterRepository = mathChapterRepository;
        this.mathSkillRepository = mathSkillRepository;
        this.studentMetricRepository = studentMetricRepository;
        this.parentQuestRepository = parentQuestRepository;
        this.parentRewardRepository = parentRewardRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    // ── Deterministic Test Identity UUIDs ─────────────────────────────────────
    public static final UUID ADMIN_UUID    = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID TEACHER_UUID  = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID STUDENT1_UUID = UUID.fromString("33333333-3333-3333-3333-333333333331"); // Arnav
    public static final UUID STUDENT2_UUID = UUID.fromString("33333333-3333-3333-3333-333333333332"); // Alisha
    public static final UUID PARENT1_UUID  = UUID.fromString("44444444-4444-4444-4444-444444444441"); // Arnav's Parent
    public static final UUID PARENT2_UUID  = UUID.fromString("44444444-4444-4444-4444-444444444442"); // Alisha's Parent
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public void run(String... args) throws Exception {
        System.out.println("\n--- STARTING DEMO TEST HARNESS ---");

        Integer tenantCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tenants", Integer.class);

        UUID activeTenantId;
        UUID activeAcademicYearId;
        UUID activeUserId;

        if (tenantCount != null && tenantCount > 0) {
            System.out.println(">> Persistent data detected in PostgreSQL. Skipping core seed setup.");
            activeTenantId = jdbcTemplate.queryForObject("SELECT id FROM tenants LIMIT 1", UUID.class);
            activeAcademicYearId = jdbcTemplate.queryForObject("SELECT id FROM academic_years LIMIT 1", UUID.class);
            activeUserId = jdbcTemplate.queryForObject("SELECT id FROM users LIMIT 1", UUID.class);
        } else {
            activeTenantId = UUID.randomUUID();
            activeAcademicYearId = UUID.randomUUID();
            // Use the explicit Admin UUID for the first seeded user
            activeUserId = ADMIN_UUID;

            System.out.println(">> Setting up mock Tenant, Academic Year, and Admin User (" + ADMIN_UUID + ")...");
            jdbcTemplate.update("INSERT INTO tenants (id, name, subdomain, is_active, created_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                activeTenantId, "Greenwood High", "greenwood_" + activeTenantId.toString().substring(0, 8), true);

            jdbcTemplate.update("INSERT INTO academic_years (id, tenant_id, name, start_date, end_date, is_current) VALUES (?, ?, ?, CURRENT_DATE, CURRENT_DATE, ?)",
                activeAcademicYearId, activeTenantId, "2026-2027", true);

            // Admin user with pinned UUID
            jdbcTemplate.update("INSERT INTO users (id, tenant_id, academic_year_id, email, password_hash, full_name, role, is_active) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                ADMIN_UUID, activeTenantId, activeAcademicYearId, "admin@greenwood.com", passwordEncoder.encode("PilotLaunchSecure2026!"), "Admin User", "ADMIN", true);

            // Teacher user with pinned UUID
            jdbcTemplate.update("INSERT INTO users (id, tenant_id, academic_year_id, email, password_hash, full_name, role, is_active) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                TEACHER_UUID, activeTenantId, activeAcademicYearId, "teacher@greenwood.com", passwordEncoder.encode("PilotLaunchSecure2026!"), "Class Teacher", "TEACHER", true);

            Announcement announcement = new Announcement();
            announcement.setTitle("Rain Holiday");
            announcement.setContent("School closed tomorrow due to heavy monsoon rains.");
            announcement.setTargetGrade("Grade 6");
            announcement.setTenantId(activeTenantId);
            announcement.setAcademicYearId(activeAcademicYearId);
            announcement.setCreatedBy(ADMIN_UUID);

            System.out.println(">> Saving Announcement...");
            announcementService.createAnnouncement(announcement);
        }

        if (classSectionRepository.count() == 0) {
            System.out.println(">> Seeding Grade 6-A Roster...");

            ClassSection classSection = new ClassSection();
            classSection.setId(UUID.randomUUID());
            classSection.setTenantId(activeTenantId);
            classSection.setAcademicYearId(activeAcademicYearId);
            classSection.setGradeName("Grade 6");
            classSection.setSectionName("A");
            classSection.setRoomNumber("Room 101");
            classSection = classSectionRepository.save(classSection);

            // Parent 1 → Arnav's Parent (pinned UUID: 44444444-...-441)
            Parent p1 = new Parent();
            p1.setId(PARENT1_UUID);
            p1.setTenantId(activeTenantId);
            p1.setAcademicYearId(activeAcademicYearId);
            p1.setFirstName("Rajesh");
            p1.setLastName("Sharma");
            p1.setPhoneNumber("+91 99000 11221");
            parentRepository.save(p1);

            // Parent 2 → Alisha's Parent (pinned UUID: 44444444-...-442)
            Parent p2 = new Parent();
            p2.setId(PARENT2_UUID);
            p2.setTenantId(activeTenantId);
            p2.setAcademicYearId(activeAcademicYearId);
            p2.setFirstName("Amit");
            p2.setLastName("Patel");
            p2.setPhoneNumber("+91 99000 11222");
            parentRepository.save(p2);

            // Parent 3 — extra parent (random UUID; no strict test identity needed)
            Parent p3 = new Parent();
            p3.setId(UUID.randomUUID());
            p3.setTenantId(activeTenantId);
            p3.setAcademicYearId(activeAcademicYearId);
            p3.setFirstName("Suresh");
            p3.setLastName("Das");
            p3.setPhoneNumber("+91 99000 11223");
            parentRepository.save(p3);

            // Student 1 — Arnav Sharma (pinned UUID: 33333333-...-331) → linked to Parent 1
            Student s1 = new Student();
            s1.setId(STUDENT1_UUID);
            s1.setTenantId(activeTenantId);
            s1.setAcademicYearId(activeAcademicYearId);
            s1.setFirstName("Arnav");
            s1.setLastName("Sharma");
            s1.setRollNumber("6A01");
            s1.setClassSection(classSection);
            s1.getParents().add(p1); // Parent 1 → Student 1 (Arnav)
            studentRepository.save(s1);

            // Student 2 — Alisha Patel (pinned UUID: 33333333-...-332) → linked to Parent 2
            Student s2 = new Student();
            s2.setId(STUDENT2_UUID);
            s2.setTenantId(activeTenantId);
            s2.setAcademicYearId(activeAcademicYearId);
            s2.setFirstName("Alisha");
            s2.setLastName("Patel");
            s2.setRollNumber("6A02");
            s2.setClassSection(classSection);
            s2.getParents().add(p2); // Parent 2 → Student 2 (Alisha)
            studentRepository.save(s2);

            // Student 3 — Rohan Das (random UUID; extra test student)
            Student s3 = new Student();
            s3.setId(UUID.randomUUID());
            s3.setTenantId(activeTenantId);
            s3.setAcademicYearId(activeAcademicYearId);
            s3.setFirstName("Rohan");
            s3.setLastName("Das");
            s3.setRollNumber("6A03");
            s3.setClassSection(classSection);
            s3.getParents().add(p3);
            studentRepository.save(s3);

            System.out.println(">> Seeding Math Chapters and Skills...");
            com.schoolos.academics.MathChapter ch1 = new com.schoolos.academics.MathChapter();
            ch1.setId(UUID.randomUUID());
            ch1.setTenantId(activeTenantId);
            ch1.setAcademicYearId(activeAcademicYearId);
            ch1.setTitle("Fractions");
            ch1.setSequenceNumber(1);
            mathChapterRepository.save(ch1);

            for (int i=1; i<=3; i++) {
                com.schoolos.academics.MathSkill skill = new com.schoolos.academics.MathSkill();
                skill.setId(UUID.randomUUID());
                skill.setTenantId(activeTenantId);
                skill.setAcademicYearId(activeAcademicYearId);
                skill.setSkillName("Fractions Skill " + i);
                skill.setMaxXpReward(100 * i);
                skill.setChapter(ch1);
                mathSkillRepository.save(skill);
            }

            com.schoolos.academics.MathChapter ch2 = new com.schoolos.academics.MathChapter();
            ch2.setId(UUID.randomUUID());
            ch2.setTenantId(activeTenantId);
            ch2.setAcademicYearId(activeAcademicYearId);
            ch2.setTitle("Decimals");
            ch2.setSequenceNumber(2);
            mathChapterRepository.save(ch2);

            for (int i=1; i<=3; i++) {
                com.schoolos.academics.MathSkill skill = new com.schoolos.academics.MathSkill();
                skill.setId(UUID.randomUUID());
                skill.setTenantId(activeTenantId);
                skill.setAcademicYearId(activeAcademicYearId);
                skill.setSkillName("Decimals Skill " + i);
                skill.setMaxXpReward(100 * i);
                skill.setChapter(ch2);
                mathSkillRepository.save(skill);
            }

            System.out.println(">> Seeding Student Metrics, Quests, and Rewards...");
            for (Student s : new Student[]{s1, s2, s3}) {
                com.schoolos.academics.StudentMetric sm = new com.schoolos.academics.StudentMetric();
                sm.setId(UUID.randomUUID());
                sm.setTenantId(activeTenantId);
                sm.setAcademicYearId(activeAcademicYearId);
                sm.setStudent(s);
                sm.setSchoolXp(0);
                sm.setParentXp(100);
                sm.setActiveStreak(0);
                studentMetricRepository.save(sm);
            }

            ParentQuest pq = new ParentQuest();
            pq.setId(UUID.randomUUID());
            pq.setTenantId(activeTenantId);
            pq.setAcademicYearId(activeAcademicYearId);
            pq.setParent(p1);
            pq.setStudent(s1);
            pq.setTaskDescription("Clean Room");
            pq.setXpBounty(150);
            pq.setStatus("PENDING");
            parentQuestRepository.save(pq);

            ParentReward pr = new ParentReward();
            pr.setId(UUID.randomUUID());
            pr.setTenantId(activeTenantId);
            pr.setAcademicYearId(activeAcademicYearId);
            pr.setParent(p1);
            pr.setStudent(s1);
            pr.setRewardTitle("Weekend Movie Night");
            pr.setXpCost(500);
            pr.setStatus("CLAIMED_AWAITING_DELIVERY");
            parentRewardRepository.save(pr);

            System.out.println(">> Seeding Grade 6-A Roster... Complete!");
        } else {
            System.out.println(">> Class sections already exist. Skipping roster seed.");
        }

        System.out.println(">> Retrieving Announcements...");
        List<Announcement> retrieved = announcementRepository.findAll();
        System.out.println("[SUCCESS] Retrieved " + retrieved.size() + " announcement(s) currently saved on disk!");

        String userEmail = jdbcTemplate.queryForObject("SELECT email FROM users LIMIT 1", String.class);
        System.out.println("\n===========================================================");
        System.out.println("[TEST CREDENTIALS] Use these to log in at /web/login:");
        System.out.println("Email: " + userEmail);
        System.out.println("Password: PilotLaunchSecure2026!");
        System.out.println("===========================================================\n");

        System.out.println("--- DEMO TEST HARNESS COMPLETED ---\n");
    }
}

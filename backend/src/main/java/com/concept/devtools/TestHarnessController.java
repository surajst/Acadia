package com.concept.devtools;
import com.concept.fees.app.FeeManagementService;
import com.concept.assignment.app.SubjectAssignmentService;
import com.concept.oversight.data.StudentProgressRepository;
import com.concept.shared.data.AcademicSubmissionRepository;
import com.concept.parent.data.ParentRewardRepository;
import com.concept.parent.data.ParentQuestRepository;
import com.concept.parent.data.ParentQuest;
import com.concept.rewards.data.RewardItemRepository;
import com.concept.tasks.data.TaskType;
import com.concept.tasks.data.TeacherTaskRepository;
import com.concept.tasks.data.TeacherTask;
import com.concept.assignment.data.SubjectAssignmentRepository;
import com.concept.assignment.data.SubjectAssignment;
import com.concept.notification.data.NotificationRepository;
import com.concept.notification.data.Notification;
import com.concept.fees.data.FeeTransactionRepository;
import com.concept.fees.data.FeeInvoiceRepository;
import com.concept.shared.data.AttendanceStatus;
import com.concept.shared.data.AttendanceRepository;
import com.concept.shared.data.Attendance;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ParentRepository;
import com.concept.shared.data.Parent;
import com.concept.shared.data.StudentRepository;
import com.concept.shared.data.Student;

import com.concept.academics.StudentMetric;
import com.concept.academics.StudentMetricRepository;
import com.concept.user.User;
import com.concept.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Dev-only test harness. Hosts {@code /test/reset}, which reseeds a deterministic
 * demo dataset (the Arjun Sharma student, quests, tasks, attendance, fees, etc.)
 * that the Playwright suite relies on. Every path is hard-gated behind
 * {@code app.dev-mode}; in production the endpoint 403s and does nothing.
 *
 * <p>Extracted verbatim from StudentPortalController so that controller holds
 * only real student-facing endpoints and not several hundred lines of seeding.
 */
@Controller
public class TestHarnessController {

    @Autowired private StudentRepository studentRepository;
    @Autowired private StudentMetricRepository studentMetricRepository;
    @Autowired private AcademicSubmissionRepository academicSubmissionRepository;
    @Autowired private RewardItemRepository rewardItemRepository;
    @Autowired private ParentRepository parentRepository;
    @Autowired private ParentRewardRepository parentRewardRepository;
    @Autowired private ParentQuestRepository parentQuestRepository;
    @Autowired private FeeInvoiceRepository feeInvoiceRepository;
    @Autowired private FeeTransactionRepository feeTransactionRepository;
    @Autowired private FeeManagementService feeManagementService;
    @Autowired private StudentProgressRepository studentProgressRepository;
    @Autowired private TeacherTaskRepository teacherTaskRepository;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private SubjectAssignmentRepository subjectAssignmentRepository;
    @Autowired private SubjectAssignmentService subjectAssignmentService;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationRepository notificationRepository;

    @Value("${app.dev-mode:false}")
    private boolean devMode;

    @GetMapping("/test/reset")
    @ResponseBody
    @Transactional
    public String testReset() {
        if (!devMode) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Test reset is disabled in production");
        }
        try {
            UUID arjunId = UUID.fromString("00000000-0000-0000-0000-000000000091");
            StudentMetric metric = studentMetricRepository.findByStudentId(arjunId).orElse(null);

            // Resolve active tenant details from database
            UUID activeTenantId = null;
            UUID activeAcademicYearId = null;
            for (Student s : studentRepository.findAll()) {
                if (s.getTenantId() != null) {
                    activeTenantId = s.getTenantId();
                    activeAcademicYearId = s.getAcademicYearId();
                    break;
                }
            }
            if (activeTenantId == null) {
                activeTenantId = UUID.fromString("00000000-0000-0000-0000-000000000000");
                activeAcademicYearId = UUID.fromString("00000000-0000-0000-0000-111111111111");
            }

            Student student = studentRepository.findById(arjunId).orElse(null);
            if (student == null) {
                student = new Student();
                student.setId(arjunId);
                student.setFirstName("Arjun");
                student.setLastName("Sharma");
                student.setRollNumber("6A-01");
                student.setTenantId(activeTenantId);
                student.setAcademicYearId(activeAcademicYearId);

                ClassSection mockSection = new ClassSection();
                mockSection.setId(UUID.randomUUID());
                mockSection.setGradeName("Grade 6");
                mockSection.setSectionName("A");
                mockSection.setTenantId(activeTenantId);
                mockSection.setAcademicYearId(activeAcademicYearId);
                mockSection.setTeacherId(java.util.UUID.nameUUIDFromBytes("teacher_1".getBytes()));
                student.setClassSection(mockSection);
                studentRepository.saveAndFlush(student);
            } else {
                if (student.getTenantId() == null) {
                    student.setTenantId(activeTenantId);
                    student.setAcademicYearId(activeAcademicYearId);
                    if (student.getClassSection() != null) {
                        student.getClassSection().setTenantId(activeTenantId);
                        student.getClassSection().setAcademicYearId(activeAcademicYearId);
                    }
                    studentRepository.saveAndFlush(student);
                }
            }

            if (metric == null) {
                metric = new StudentMetric();
                metric.setId(UUID.randomUUID());
                metric.setStudent(student);
            }
            metric.setTenantId(activeTenantId);
            metric.setAcademicYearId(activeAcademicYearId);
            metric.setSchoolXp(300);
            metric.setParentXp(100);
            metric.setActiveStreak(5);
            studentMetricRepository.saveAndFlush(metric);

            // Clean up all parent quests and parent rewards to prevent tab leakage
            parentQuestRepository.deleteAllInBatch();
            parentRewardRepository.deleteAllInBatch();
            studentProgressRepository.deleteAllInBatch();
            academicSubmissionRepository.deleteAllInBatch();
            teacherTaskRepository.deleteAllInBatch();

            // Seed a pending parent quest for Arjun Sharma
            ParentQuest quest = new ParentQuest();
            quest.setId(UUID.randomUUID());
            quest.setTenantId(activeTenantId);
            quest.setAcademicYearId(activeAcademicYearId);
            quest.setTaskDescription("Clean your room");
            quest.setXpBounty(50);
            quest.setStatus("PENDING");

            Parent ramesh = parentRepository.findById(UUID.fromString("99999999-9999-9999-9999-999999999991")).orElse(null);
            if (ramesh == null) {
                ramesh = new Parent();
                ramesh.setId(UUID.fromString("99999999-9999-9999-9999-999999999991"));
                ramesh.setTenantId(activeTenantId);
                ramesh.setAcademicYearId(activeAcademicYearId);
                ramesh.setFirstName("Ramesh");
                ramesh.setLastName("Sharma");
                ramesh.setPhoneNumber("+91 99887 76655");
                ramesh.setEmail("ramesh.sharma@example.com");
                parentRepository.saveAndFlush(ramesh);

                if (student != null) {
                    student.getParents().add(ramesh);
                    studentRepository.saveAndFlush(student);
                }
            }
            quest.setParent(ramesh);
            quest.setStudent(student);
            parentQuestRepository.saveAndFlush(quest);

            // Seed 3 demo teacher tasks for standard 6 (assigned to whole class)
            UUID teacherId = java.util.UUID.nameUUIDFromBytes("teacher@greenwood.com".getBytes());

            TeacherTask task1 = new TeacherTask();
            task1.setId(UUID.randomUUID());
            task1.setTitle("Chapter Summary — Food and Health");
            task1.setDescription("Write a 10-line summary of Chapter 7: Food and Health. Include key nutrients and their functions.");
            task1.setSubjectCode("SCIENCE");
            task1.setTaskType(TaskType.HOMEWORK);
            task1.setStandard(6);
            task1.setAssignedToClass(true);
            task1.setCreatedByTeacherId(teacherId);
            task1.setXpReward(75);
            task1.setDueDate(LocalDate.now().plusDays(3));
            task1.setTaskStatus("ACTIVE");
            task1.setCreatedAt(java.time.LocalDateTime.now());
            teacherTaskRepository.saveAndFlush(task1);

            TeacherTask task2 = new TeacherTask();
            task2.setId(UUID.randomUUID());
            task2.setTitle("Reading: The Mughal Empire");
            task2.setDescription("Read Chapter 3 of your Social Science textbook and answer the comprehension questions below.");
            task2.setSubjectCode("SOCIAL_SCIENCE");
            task2.setTaskType(TaskType.READING);
            task2.setStandard(6);
            task2.setAssignedToClass(true);
            task2.setCreatedByTeacherId(teacherId);
            task2.setXpReward(100);
            task2.setDueDate(LocalDate.now().plusDays(5));
            task2.setTaskStatus("ACTIVE");
            task2.setCreatedAt(java.time.LocalDateTime.now());
            task2.setQuestion1("What were the main achievements of Emperor Akbar?");
            task2.setQuestion2("How did the Mughal Empire influence art and architecture in India?");
            task2.setQuestion3("Name three important battles fought during the Mughal period.");
            teacherTaskRepository.saveAndFlush(task2);

            TeacherTask task3 = new TeacherTask();
            task3.setId(UUID.randomUUID());
            task3.setTitle("Grammar Practice — Tenses");
            task3.setDescription("Complete exercises 1–10 on page 45 of your English workbook. Focus on simple past and present perfect tenses.");
            task3.setSubjectCode("ENGLISH");
            task3.setTaskType(TaskType.HOMEWORK);
            task3.setStandard(6);
            task3.setAssignedToClass(true);
            task3.setCreatedByTeacherId(teacherId);
            task3.setXpReward(50);
            task3.setDueDate(LocalDate.now().plusDays(2));
            task3.setTaskStatus("ACTIVE");
            task3.setCreatedAt(java.time.LocalDateTime.now());
            teacherTaskRepository.saveAndFlush(task3);

            // Clean up all fee billing tables and re-initialize for test idempotency
            feeTransactionRepository.deleteAllInBatch();
            feeInvoiceRepository.deleteAllInBatch();
            feeManagementService.initializeInvoices();

            // Reseed a full month of attendance records for Arjun Sharma (June 2026)
            // 18 PRESENT, 2 ABSENT, 2 TARDY across weekdays
            attendanceRepository.deleteAllInBatch();

            java.util.List<Attendance> attendanceList = new java.util.ArrayList<>();

            int[][] attendanceDays = {
                {2026, 6, 1, 0},  // Mon PRESENT
                {2026, 6, 2, 1},  // Tue ABSENT
                {2026, 6, 3, 0},  // Wed PRESENT
                {2026, 6, 4, 0},  // Thu PRESENT
                {2026, 6, 5, 0},  // Fri PRESENT
                {2026, 6, 8, 0},  // Mon PRESENT
                {2026, 6, 9, 0},  // Tue PRESENT
                {2026, 6, 10, 2}, // Wed TARDY
                {2026, 6, 11, 0}, // Thu PRESENT
                {2026, 6, 12, 0}, // Fri PRESENT
                {2026, 6, 15, 0}, // Mon PRESENT
                {2026, 6, 16, 1}, // Tue ABSENT
                {2026, 6, 17, 0}, // Wed PRESENT
                {2026, 6, 18, 0}, // Thu PRESENT
                {2026, 6, 19, 2}, // Fri TARDY
                {2026, 6, 20, 0}, // Mon PRESENT
            };
            AttendanceStatus[] statuses = { AttendanceStatus.PRESENT, AttendanceStatus.ABSENT, AttendanceStatus.TARDY };
            for (int[] day : attendanceDays) {
                Attendance att = new Attendance();
                att.setId(UUID.randomUUID());
                att.setTenantId(activeTenantId);
                att.setAcademicYearId(activeAcademicYearId);
                att.setStudent(student);
                att.setClassSection(student.getClassSection());
                att.setAttendanceDate(LocalDate.of(day[0], day[1], day[2]));
                att.setStatus(statuses[day[3]]);
                attendanceList.add(att);
            }
            attendanceRepository.saveAllAndFlush(attendanceList);

            // ── Ensure pilot SubjectAssignment exists (idempotent) ──────────────
            // This replaces the now-secured /api/admin/assignments/seed endpoint for tests.
            // The "Admin can remove an assignment" test deletes this row; re-seeding it here
            // guarantees a clean, consistent state before every test that depends on it.
            try {
                final String PILOT_TEACHER_EMAIL = "teacher@greenwood.com";
                final UUID   PILOT_SECTION_ID    = UUID.fromString("66666666-6666-6666-6666-666666666666");

                com.concept.user.User pilotTeacher = userRepository.findByEmail(PILOT_TEACHER_EMAIL).orElse(null);
                ClassSection pilotSection = classSectionRepository.findById(PILOT_SECTION_ID).orElse(null);

                if (pilotTeacher != null && pilotSection != null) {
                    boolean hasHomeClassAssignment = subjectAssignmentRepository.findByTeacher(pilotTeacher)
                        .stream()
                        .anyMatch(a -> a.getClassSection().getId().equals(PILOT_SECTION_ID) && a.isHomeClass());
                    if (!hasHomeClassAssignment) {
                        subjectAssignmentService.assignSubject(
                                pilotTeacher.getId(), PILOT_SECTION_ID, "Mathematics", true, null);
                        System.err.println("--- TEST RESET: pilot SubjectAssignment seeded ---");
                    } else {
                        System.err.println("--- TEST RESET: pilot SubjectAssignment already present ---");
                    }
                } else {
                    System.err.println("--- TEST RESET: pilot teacher or section not found — skipping assignment seed ---");
                }
            } catch (Exception assignEx) {
                System.err.println("--- TEST RESET: assignment seed failed: " + assignEx.getMessage() + " ---");
            }

            try {
                User pilotTeacher = userRepository.findByEmail("teacher@greenwood.com").orElse(null);
                if (pilotTeacher != null) {
                    List<Notification> existingNotifs = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(pilotTeacher.getId());
                    if (existingNotifs.isEmpty()) {
                        String[][] notifs = {
                            {"Attendance Reminder", "You have 2 classes pending attendance today.", "ATTENDANCE"},
                            {"New Task Submitted", "Arjun Sharma submitted Grammar Practice — Tenses.", "TASK"},
                            {"School Announcement", "Staff meeting scheduled for Friday 3 PM.", "ANNOUNCEMENT"}
                        };
                        for (String[] n : notifs) {
                            Notification notif = new Notification();
                            notif.setId(UUID.randomUUID());
                            notif.setTenantId(pilotTeacher.getTenantId());
                            notif.setAcademicYearId(pilotTeacher.getAcademicYearId());
                            notif.setRecipientId(pilotTeacher.getId());
                            notif.setRecipientRole("TEACHER");
                            notif.setTitle(n[0]);
                            notif.setBody(n[1]);
                            notif.setType(n[2]);
                            notificationRepository.save(notif);
                        }
                    }
                }
            } catch (Exception notifEx) {
                System.err.println("--- TEST RESET: notification seed failed: " + notifEx.getMessage() + " ---");
            }

            System.err.println("--- TEST RESET COMPLETED: 3 tasks seeded, full attendance month seeded ---");
            return "OK";
        } catch (Exception e) {
            e.printStackTrace();
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            return "ERROR: " + e.getMessage() + "\n" + sw.toString();
        }
    }
}

package com.schoolos.management;

import com.schoolos.academics.MathSkill;
import com.schoolos.academics.MathSkillRepository;
import com.schoolos.academics.StudentMetric;
import com.schoolos.academics.StudentMetricRepository;
import com.schoolos.user.CurrentUserService;
import com.schoolos.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Collections;

import com.schoolos.management.Notification;
import com.schoolos.management.NotificationRepository;
import com.schoolos.user.User;

@Controller
public class StudentPortalController {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private StudentMetricRepository studentMetricRepository;

    @Autowired
    private AcademicSubmissionRepository academicSubmissionRepository;

    @Autowired
    private MathSkillRepository mathSkillRepository;

    @Autowired
    private RewardItemRepository rewardItemRepository;

    @Autowired
    private ParentRepository parentRepository;

    @Autowired
    private ParentRewardRepository parentRewardRepository;

    @Autowired
    private ParentQuestRepository parentQuestRepository;

    @Autowired
    private FeeInvoiceRepository feeInvoiceRepository;

    @Autowired
    private FeeTransactionRepository feeTransactionRepository;

    @Autowired
    private FeeManagementService feeManagementService;

    @Autowired
    private StudentProgressRepository studentProgressRepository;

    @Autowired
    private TeacherTaskRepository teacherTaskRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private SubjectAssignmentRepository subjectAssignmentRepository;

    @Autowired
    private SubjectAssignmentService subjectAssignmentService;

    @Autowired
    private ClassSectionRepository classSectionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CurrentUserService currentUserService;

    @Autowired
    private StudentRewardService studentRewardService;

    @Value("${app.dev-mode:false}")
    private boolean devMode;

    private Student resolveStudent(Authentication authentication) {
        return currentUserService.getCurrentStudent(authentication)
                .orElseThrow(() -> new IllegalArgumentException("Student record not found"));
    }


    @GetMapping("/web/student/portal")
    public String getStudentPortal(@RequestParam(value = "tab", required = false, defaultValue = "dashboard") String activeTab,
                                   Model model, Authentication authentication) {
        model.addAttribute("activeTab", activeTab);
        Student student = resolveStudent(authentication);

        UUID studentId = student.getId();

        StudentMetric studentMetrics = null;
        try {
            studentMetrics = studentMetricRepository.findByStudentId(studentId).orElse(null);
        } catch (Exception e) {
            // gracefully catch any repository issues
        }

        if (studentMetrics == null) {
            studentMetrics = new StudentMetric();
            studentMetrics.setId(UUID.randomUUID());
            studentMetrics.setStudent(student);
            studentMetrics.setTenantId(student.getTenantId());
            studentMetrics.setAcademicYearId(student.getAcademicYearId());
            studentMetrics.setSchoolXp(0);
            studentMetrics.setParentXp(0);
            studentMetrics.setActiveStreak(0);
            try {
                studentMetricRepository.saveAndFlush(studentMetrics);
            } catch (Exception e) {
                // gracefully catch
            }
        }

        System.err.println("--- GET STUDENT PORTAL: studentId=" + studentId + " schoolXp=" + studentMetrics.getSchoolXp() + " parentXp=" + studentMetrics.getParentXp() + " ---");

        int totalXp = studentMetrics.getSchoolXp() != null ? studentMetrics.getSchoolXp() : 0;
        int scholarLevel = (totalXp / 500) + 1;
        int levelProgress = (totalXp % 500) * 100 / 500;
        int xpToNextLevel = 500 - (totalXp % 500);

        List<AcademicSubmission> submissions = null;
        try {
            submissions = academicSubmissionRepository.findByStudentId(studentId);
        } catch (Exception e) {
            // gracefully catch any repository issues
        }
        if (submissions == null) {
            submissions = List.of();
        }

        List<MathSkill> availableSkills = null;
        try {
            availableSkills = mathSkillRepository.findAll();
        } catch (Exception e) {
            // gracefully catch any repository issues
        }

        if (availableSkills == null || availableSkills.isEmpty()) {
            MathSkill skill1 = new MathSkill();
            skill1.setId(UUID.randomUUID());
            skill1.setSkillName("6th Grade Fraction Mastery");
            skill1.setMaxXpReward(250);

            MathSkill skill2 = new MathSkill();
            skill2.setId(UUID.randomUUID());
            skill2.setSkillName("Basic Fractions");
            skill2.setMaxXpReward(250);

            availableSkills = List.of(skill1, skill2);
        }

        List<RewardItem> rewardInventoryList = null;
        try {
            rewardInventoryList = rewardItemRepository.findAll();
        } catch (Exception e) {
            // gracefully catch any repository issues
        }
        if (rewardInventoryList == null) {
            rewardInventoryList = List.of();
        }

        List<ParentReward> pendingList = null;
        List<String> pendingRewardTitles = new java.util.ArrayList<>();
        try {
            pendingList = parentRewardRepository.findByStudentIdAndStatus(studentId, "PENDING");
            if (pendingList != null) {
                for (ParentReward pr : pendingList) {
                    if (pr.getRewardTitle() != null) {
                        pendingRewardTitles.add(pr.getRewardTitle());
                    }
                }
            }
        } catch (Exception e) {
            // gracefully catch any repository issues
        }
        model.addAttribute("parent_rewards", pendingList != null ? pendingList : List.of());
        model.addAttribute("pendingRewardTitles", pendingRewardTitles);

        List<ParentQuest> parentQuests = List.of();
        try {
            parentQuests = parentQuestRepository.findByStudentId(studentId);
            System.err.println("parentQuests size=" + parentQuests.size());
            if (parentQuests.size() > 0) {
                System.err.println("First quest desc=" + parentQuests.get(0).getTaskDescription() + " status=" + parentQuests.get(0).getStatus());
            }
        } catch (Exception e) {
            System.err.println("EXCEPTION IN parentQuestRepository: " + e.getMessage());
        }
        model.addAttribute("parentQuests", parentQuests);

        List<ParentReward> parentRewards = List.of();
        try {
            parentRewards = parentRewardRepository.findByStudentIdAndStatus(studentId, "AVAILABLE");
        } catch (Exception e) {
            // gracefully catch
        }
        model.addAttribute("parentRewards", parentRewards);

        model.addAttribute("student", student);
        model.addAttribute("studentMetrics", studentMetrics);
        model.addAttribute("totalXp", totalXp);
        model.addAttribute("scholarLevel", scholarLevel);
        model.addAttribute("levelProgress", levelProgress);
        model.addAttribute("xpToNextLevel", xpToNextLevel);
        model.addAttribute("submissions", submissions);
        model.addAttribute("availableSkills", availableSkills);
        model.addAttribute("rewardInventoryList", rewardInventoryList);
        model.addAttribute("currentDate", LocalDate.now());
        model.addAttribute("systemScope", "STUDENT_PORTAL");

        String role = "STUDENT";
        if (authentication != null) {
            for (GrantedAuthority auth : authentication.getAuthorities()) {
                String authority = auth.getAuthority();
                if (authority.startsWith("ROLE_")) {
                    role = authority.substring(5);
                }
            }
        }
        model.addAttribute("currentUserRole", role);

        return "student_portal";
    }

    @PostMapping("/web/student/submit-milestone")
    public String submitMilestone(@RequestParam("skillName") String skillName,
                                  @RequestParam("proofOfWorkNotes") String proofOfWorkNotes,
                                  @RequestParam(value = "answer1", required = false) String answer1,
                                  @RequestParam(value = "answer2", required = false) String answer2,
                                  @RequestParam(value = "answer3", required = false) String answer3,
                                  @RequestParam(value = "teacherTaskId", required = false) UUID teacherTaskId,
                                  Authentication authentication) {
        int bounty = 250;
        if (teacherTaskId != null) {
            try {
                TeacherTask task = teacherTaskRepository.findById(teacherTaskId).orElse(null);
                if (task != null && task.getXpReward() != null) {
                    bounty = task.getXpReward();
                }
            } catch (Exception e) {
                // gracefully catch
            }
        } else {
            // Fallback: Check if skillName matches a TeacherTask title
            try {
                UUID currentTenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
                List<TeacherTask> tenantTasks = currentTenantId != null
                        ? teacherTaskRepository.findByTenantId(currentTenantId)
                        : List.of();
                for (TeacherTask task : tenantTasks) {
                    if (task.getTitle().equalsIgnoreCase(skillName)) {
                        bounty = task.getXpReward() != null ? task.getXpReward() : 50;
                        teacherTaskId = task.getId();
                        break;
                    }
                }
            } catch (Exception e) {
                // gracefully catch
            }

            // Only fallback to MathSkill if still 250
            if (bounty == 250) {
                List<MathSkill> skills = null;
                try {
                    skills = mathSkillRepository.findAll();
                } catch (Exception e) {
                    // gracefully catch
                }
                if (skills != null) {
                    for (MathSkill s : skills) {
                        if (s.getSkillName().equalsIgnoreCase(skillName)) {
                            bounty = s.getMaxXpReward() != null ? s.getMaxXpReward() : 250;
                            break;
                        }
                    }
                }
            }
        }

        Student student = resolveStudent(authentication);
        UUID studentId = student.getId();

        AcademicSubmission submission = new AcademicSubmission();
        submission.setId(UUID.randomUUID());
        submission.setStudentId(studentId);
        submission.setSkillName(skillName);
        submission.setXpBounty(bounty);
        submission.setStatus("PENDING");
        submission.setProofOfWorkNotes(proofOfWorkNotes);
        submission.setAnswer1(answer1);
        submission.setAnswer2(answer2);
        submission.setAnswer3(answer3);
        submission.setTeacherTaskId(teacherTaskId);
        submission.setSubmittedAt(LocalDateTime.now());

        try {
            academicSubmissionRepository.saveAndFlush(submission);
        } catch (Exception e) {
            // gracefully catch
        }

        return "redirect:/web/student/portal?success=true";
    }

    @Transactional
    @PostMapping("/web/student/rewards/redeem")
    public String redeemReward(@RequestParam("rewardId") UUID rewardId, Authentication authentication) {
        Student student = resolveStudent(authentication);
        StudentRewardService.RedeemOutcome outcome = studentRewardService.redeemReward(rewardId, student);
        if (outcome == StudentRewardService.RedeemOutcome.INSUFFICIENT_XP) {
            return "redirect:/web/student/portal?tab=rewards&error=insufficient_xp";
        }
        if (outcome == StudentRewardService.RedeemOutcome.NO_LINKED_PARENT) {
            return "redirect:/web/student/portal?tab=rewards&error=no_linked_parent";
        }
        return "redirect:/web/student/portal?tab=rewards&success=redeemed";
    }

    @PostMapping("/web/student/quest/{id}/claim")
    public String claimQuest(@PathVariable("id") UUID id, Authentication authentication) {
        studentRewardService.claimQuest(id, resolveStudent(authentication));
        return "redirect:/web/student/portal?success=quest_claimed";
    }

    @PostMapping("/web/student/reward/{id}/redeem")
    public String redeemParentReward(@PathVariable("id") UUID id, Authentication authentication) {
        Student student = resolveStudent(authentication);
        StudentRewardService.RedeemOutcome outcome = studentRewardService.redeemParentReward(id, student);
        if (outcome == StudentRewardService.RedeemOutcome.INSUFFICIENT_XP) {
            return "redirect:/web/student/portal?tab=rewards&error=insufficient_xp";
        }
        return "redirect:/web/student/portal?tab=rewards&success=reward_redeemed";
    }

    @ExceptionHandler(ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<String> handleResponseStatusException(ResponseStatusException ex) {
        return org.springframework.http.ResponseEntity.status(ex.getStatusCode()).body(ex.getReason());
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public String handleException(Exception ex, Model model) {
        System.err.println("--- STUDENT PORTAL CONTROLLER EXCEPTION DETECTED ---");
        ex.printStackTrace();
        java.io.StringWriter sw = new java.io.StringWriter();
        ex.printStackTrace(new java.io.PrintWriter(sw));
        return "GLOBAL_EXCEPTION: " + ex.getMessage() + "\n" + sw.toString();
    }
}

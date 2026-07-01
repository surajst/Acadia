package com.schoolos.academics;

import com.schoolos.management.ParentQuest;
import com.schoolos.management.ParentQuestRepository;
import com.schoolos.management.ParentReward;
import com.schoolos.management.ParentRewardRepository;
import com.schoolos.management.Student;
import com.schoolos.management.StudentRepository;
import com.schoolos.academics.MathSkill;
import com.schoolos.academics.MathSkillRepository;
import com.schoolos.academics.StudentMetric;
import com.schoolos.academics.StudentMetricRepository;
import com.schoolos.user.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/web/student")
public class StudentPortalWebController {

    private final StudentRepository studentRepository;
    private final StudentMetricRepository studentMetricRepository;
    private final MathSkillRepository mathSkillRepository;
    private final ParentQuestRepository parentQuestRepository;
    private final ParentRewardRepository parentRewardRepository;

    public StudentPortalWebController(StudentRepository studentRepository,
                                      StudentMetricRepository studentMetricRepository,
                                      MathSkillRepository mathSkillRepository,
                                      ParentQuestRepository parentQuestRepository,
                                      ParentRewardRepository parentRewardRepository) {
        this.studentRepository = studentRepository;
        this.studentMetricRepository = studentMetricRepository;
        this.mathSkillRepository = mathSkillRepository;
        this.parentQuestRepository = parentQuestRepository;
        this.parentRewardRepository = parentRewardRepository;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, HttpSession session) {
        // Mock session logic: fetch Aarav explicitly as requested
        Student student = studentRepository.findAll().stream()
                .filter(s -> s.getFirstName().equals("Aarav"))
                .findFirst()
                .orElse(null);

        if (student == null) {
            return "redirect:/web/feed";
        }

        StudentMetric metric = studentMetricRepository.findByStudentId(student.getId()).orElse(new StudentMetric());
        List<MathSkill> mathSkills = mathSkillRepository.findByChapterSequenceNumber(1);
        List<ParentQuest> parentQuests = parentQuestRepository.findByStudentId(student.getId());
        List<ParentReward> awaitingDeliveryRewards = parentRewardRepository.findByStudentIdAndStatus(student.getId(), "CLAIMED_AWAITING_DELIVERY");

        model.addAttribute("student", student);
        model.addAttribute("metric", metric);
        model.addAttribute("mathSkills", mathSkills);
        model.addAttribute("parentQuests", parentQuests);
        model.addAttribute("awaitingDeliveryRewards", awaitingDeliveryRewards);

        return "student_dashboard";
    }
}

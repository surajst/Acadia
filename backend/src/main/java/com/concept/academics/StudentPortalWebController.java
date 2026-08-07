package com.concept.academics;

import com.concept.management.ParentQuest;
import com.concept.management.ParentQuestRepository;
import com.concept.management.ParentReward;
import com.concept.management.ParentRewardRepository;
import com.concept.management.Student;
import com.concept.management.StudentRepository;
import com.concept.academics.MathSkill;
import com.concept.academics.MathSkillRepository;
import com.concept.academics.StudentMetric;
import com.concept.academics.StudentMetricRepository;
import com.concept.user.CurrentUserService;
import com.concept.user.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
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
    private final CurrentUserService currentUserService;

    public StudentPortalWebController(StudentRepository studentRepository,
                                      StudentMetricRepository studentMetricRepository,
                                      MathSkillRepository mathSkillRepository,
                                      ParentQuestRepository parentQuestRepository,
                                      ParentRewardRepository parentRewardRepository,
                                      CurrentUserService currentUserService) {
        this.studentRepository = studentRepository;
        this.studentMetricRepository = studentMetricRepository;
        this.mathSkillRepository = mathSkillRepository;
        this.parentQuestRepository = parentQuestRepository;
        this.parentRewardRepository = parentRewardRepository;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "redirect:/web/student/portal";
    }
}

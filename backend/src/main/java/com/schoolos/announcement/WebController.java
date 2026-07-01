package com.schoolos.announcement;

import com.schoolos.user.CurrentUserService;
import com.schoolos.user.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.schoolos.management.StudentRepository;
import com.schoolos.management.AttendanceRepository;
import com.schoolos.management.AttendanceStatus;

@Controller
@RequestMapping("/web")
public class WebController {

    private final AnnouncementService announcementService;
    private final AnnouncementRepository announcementRepository;
    private final StudentRepository studentRepository;
    private final AttendanceRepository attendanceRepository;
    private final CurrentUserService currentUserService;

    public WebController(AnnouncementService announcementService,
                         AnnouncementRepository announcementRepository,
                         StudentRepository studentRepository,
                         AttendanceRepository attendanceRepository,
                         CurrentUserService currentUserService) {
        this.announcementService = announcementService;
        this.announcementRepository = announcementRepository;
        this.studentRepository = studentRepository;
        this.attendanceRepository = attendanceRepository;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/feed")
    public String feed(Model model, HttpSession session) {
        List<Announcement> announcements = announcementRepository.findAll();
        // Sort descending by creation date (newest first)
        announcements.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        model.addAttribute("announcements", announcements);

        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser != null) {
            model.addAttribute("userName", currentUser.getFullName());
        }

        long totalStudentsCount = studentRepository.count();
        long absencesCount = attendanceRepository.countByAttendanceDateAndStatus(LocalDate.now(), AttendanceStatus.ABSENT);
        
        long attendanceRate = 100;
        if (totalStudentsCount > 0) {
            attendanceRate = ((totalStudentsCount - absencesCount) * 100) / totalStudentsCount;
        }

        model.addAttribute("totalStudentsCount", totalStudentsCount);
        model.addAttribute("absencesCount", absencesCount);
        model.addAttribute("attendanceRate", attendanceRate);

        return "feed";
    }

    @GetMapping("/admin")
    public String admin() {
        return "admin";
    }

    @PostMapping("/admin/post")
    public String postAnnouncement(@ModelAttribute Announcement announcement, Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication)
                .orElseThrow(() -> new IllegalStateException("Authenticated admin user not found"));

        announcement.setTenantId(currentUser.getTenantId());
        announcement.setAcademicYearId(currentUser.getAcademicYearId());
        announcement.setCreatedBy(currentUser.getId());

        // Service handles saving and prints the WhatsApp mock console output
        announcementService.createAnnouncement(announcement);

        return "redirect:/web/admin/dashboard?success=announcement_posted";
    }
}

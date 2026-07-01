package com.schoolos.announcement;

import com.schoolos.user.User;
import com.schoolos.user.UserRepository;
import jakarta.servlet.http.HttpSession;
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
    private final UserRepository userRepository;

    public WebController(AnnouncementService announcementService,
                         AnnouncementRepository announcementRepository,
                         StudentRepository studentRepository,
                         AttendanceRepository attendanceRepository,
                         UserRepository userRepository) {
        this.announcementService = announcementService;
        this.announcementRepository = announcementRepository;
        this.studentRepository = studentRepository;
        this.attendanceRepository = attendanceRepository;
        this.userRepository = userRepository;
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
    public String postAnnouncement(@ModelAttribute Announcement announcement) {
        // Resolve the real admin user UUID dynamically using the canonical Admin identity
        // Falls back to the pinned Admin UUID: 11111111-1111-1111-1111-111111111111
        UUID resolvedCreatedBy = userRepository.findByEmail("admin@greenwood.com")
                .map(User::getId)
                .orElseGet(() -> userRepository.findAll().stream()
                        .findFirst()
                        .map(User::getId)
                        .orElse(UUID.fromString("11111111-1111-1111-1111-111111111111")));

        // Strategy 1: source tenant context from existing announcements
        List<Announcement> existing = announcementRepository.findAll();
        if (!existing.isEmpty()) {
            Announcement ref = existing.get(0);
            announcement.setTenantId(ref.getTenantId());
            announcement.setAcademicYearId(ref.getAcademicYearId());
            announcement.setCreatedBy(ref.getCreatedBy() != null ? ref.getCreatedBy() : resolvedCreatedBy);
        } else {
            // Strategy 2: fall back to the first student record (always seeded)
            studentRepository.findAll().stream().findFirst().ifPresentOrElse(student -> {
                announcement.setTenantId(student.getTenantId());
                announcement.setAcademicYearId(student.getAcademicYearId());
                announcement.setCreatedBy(resolvedCreatedBy);
            }, () -> {
                // Strategy 3: absolute fallback — use the static seeded tenant UUIDs
                announcement.setTenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"));
                announcement.setAcademicYearId(UUID.fromString("00000000-0000-0000-0000-111111111111"));
                announcement.setCreatedBy(resolvedCreatedBy);
            });
        }

        // Service handles saving and prints the WhatsApp mock console output
        announcementService.createAnnouncement(announcement);

        return "redirect:/web/admin/dashboard?success=announcement_posted";
    }
}

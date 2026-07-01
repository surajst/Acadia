package com.schoolos.management;

import com.schoolos.user.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.schoolos.management.Parent;

// NOTE: This controller is intentionally disabled.
// Attendance routes are owned by UnifiedDashboardWebController (/web/teacher/attendance).
// Re-enabling this would cause AmbiguousRequestMappingException at startup.
// @Controller
// @RequestMapping("/web/management")
public class AttendanceWebController {

    private final ClassSectionRepository classSectionRepository;
    private final StudentRepository studentRepository;
    private final AttendanceRepository attendanceRepository;

    public AttendanceWebController(ClassSectionRepository classSectionRepository, 
                                   StudentRepository studentRepository, 
                                   AttendanceRepository attendanceRepository) {
        this.classSectionRepository = classSectionRepository;
        this.studentRepository = studentRepository;
        this.attendanceRepository = attendanceRepository;
    }

    @GetMapping("/attendance")
    public String showAttendanceForm(Model model, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null) {
            return "redirect:/web/login";
        }
        
        List<ClassSection> sections = classSectionRepository.findAll();
        if (sections.isEmpty()) {
            return "redirect:/web/feed";
        }
        
        ClassSection section = sections.get(0);
        List<Student> students = studentRepository.findAll();
        
        model.addAttribute("section", section);
        model.addAttribute("students", students);
        model.addAttribute("currentDate", LocalDate.now());
        
        return "attendance";
    }

    @PostMapping("/attendance/submit")
    public String submitAttendance(@RequestParam("studentIds") List<UUID> studentIds,
                                   @RequestParam("statuses") List<AttendanceStatus> statuses,
                                   HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null) {
            return "redirect:/web/login";
        }

        List<ClassSection> sections = classSectionRepository.findAll();
        if (sections.isEmpty()) return "redirect:/web/feed";
        ClassSection section = sections.get(0);

        LocalDate today = LocalDate.now();

        for (int i = 0; i < studentIds.size(); i++) {
            UUID studentId = studentIds.get(i);
            AttendanceStatus status = statuses.get(i);
            
            Student student = studentRepository.findById(studentId).orElse(null);
            if (student != null) {
                Attendance attendance = new Attendance();
                attendance.setId(UUID.randomUUID());
                attendance.setTenantId(student.getTenantId());
                attendance.setAcademicYearId(student.getAcademicYearId());
                attendance.setStudent(student);
                attendance.setClassSection(section);
                attendance.setAttendanceDate(today);
                attendance.setStatus(status);
                
                attendanceRepository.save(attendance);
                
                if (status == AttendanceStatus.ABSENT) {
                    for (Parent parent : student.getParents()) {
                        System.out.println("[ALERT WHATSAPP DISPATCH] Sending to " + parent.getFirstName() + " " + parent.getLastName() + " (" + parent.getPhoneNumber() + "): Alert! Student " + student.getFirstName() + " was marked ABSENT today.");
                    }
                }
            }
        }

        return "redirect:/web/teacher/attendance?success=true";
    }
}

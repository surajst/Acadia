package com.schoolos.management;

import com.schoolos.user.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.UUID;
import com.schoolos.management.ClassSection;
import com.schoolos.management.ClassSectionRepository;
import com.schoolos.management.Student;
import com.schoolos.management.StudentRepository;
import com.schoolos.management.Parent;
import com.schoolos.management.ParentRepository;

@Controller
@RequestMapping("/web/management/upload")
public class UploadWebController {

    private final ClassSectionRepository classSectionRepository;
    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;

    public UploadWebController(ClassSectionRepository classSectionRepository, 
                               StudentRepository studentRepository, 
                               ParentRepository parentRepository) {
        this.classSectionRepository = classSectionRepository;
        this.studentRepository = studentRepository;
        this.parentRepository = parentRepository;
    }

    @GetMapping
    public String showUploadPage(HttpSession session) {
        if (session.getAttribute("currentUser") == null) {
            return "redirect:/web/login";
        }
        return "upload";
    }

    @PostMapping("/process")
    @Transactional(rollbackFor = Exception.class)
    public String processUpload(@RequestParam("file") MultipartFile file, 
                                HttpSession session, 
                                Model model) {
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null) return "redirect:/web/login";

        if (file.isEmpty()) {
            model.addAttribute("error", "Please select a valid CSV file.");
            return "upload";
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            boolean firstLine = true;
            
            while ((line = br.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue; // Skip header
                }
                
                String[] cols = line.split(",", -1);
                if (cols.length < 7) continue;

                String firstName = cols[0].trim();
                String lastName = cols[1].trim();
                String rollNumber = cols[2].trim();
                String grade = cols[3].trim();
                String section = cols[4].trim();
                String parentName = cols[5].trim();
                String parentPhone = cols[6].trim();

                if (firstName.isEmpty() || lastName.isEmpty()) continue;

                if (parentPhone.isEmpty() || !parentPhone.matches("^\\+?[0-9\\s\\-()]{7,}$")) {
                    throw new RuntimeException("Validation Error: Invalid phone number format for parent of " + firstName + " " + lastName);
                }

                // Get or create ClassSection
                ClassSection classSection = classSectionRepository.findByGradeNameAndSectionName(grade, section).orElseGet(() -> {
                    ClassSection newSection = new ClassSection();
                    newSection.setId(UUID.randomUUID());
                    newSection.setTenantId(currentUser.getTenantId());
                    newSection.setAcademicYearId(currentUser.getAcademicYearId());
                    newSection.setGradeName(grade);
                    newSection.setSectionName(section);
                    return classSectionRepository.save(newSection);
                });

                // Parent
                String pFirstName = parentName.contains(" ") ? parentName.substring(0, parentName.indexOf(" ")) : parentName;
                String pLastName = parentName.contains(" ") ? parentName.substring(parentName.indexOf(" ") + 1) : "";

                Parent parent = new Parent();
                parent.setId(UUID.randomUUID());
                parent.setTenantId(currentUser.getTenantId());
                parent.setAcademicYearId(currentUser.getAcademicYearId());
                parent.setFirstName(pFirstName);
                parent.setLastName(pLastName);
                parent.setPhoneNumber(parentPhone);
                parentRepository.save(parent);

                // Student
                Student student = new Student();
                student.setId(UUID.randomUUID());
                student.setTenantId(currentUser.getTenantId());
                student.setAcademicYearId(currentUser.getAcademicYearId());
                student.setFirstName(firstName);
                student.setLastName(lastName);
                student.setRollNumber(rollNumber);
                student.setClassSection(classSection);
                student.getParents().add(parent);
                studentRepository.save(student);
            }
        } catch (Exception e) {
            model.addAttribute("error", "Data Processing Failed: " + e.getMessage());
            return "upload";
        }

        model.addAttribute("success", "Roster successfully imported!");
        return "upload";
    }
}

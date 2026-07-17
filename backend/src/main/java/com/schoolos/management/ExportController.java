package com.schoolos.management;

import com.schoolos.user.CurrentUserService;
import com.schoolos.user.User;
import com.schoolos.user.UserRepository;
import com.schoolos.user.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin data export — tenant-scoped CSV downloads for students, staff, and
 * fees. Lets a school pull its own data back out (and lets us verify bulk
 * import round-trips). ADMIN only; every query is scoped to the caller's
 * tenant so one school can never export another's data.
 */
@RestController
@RequestMapping("/web/admin/export")
@PreAuthorize("hasRole('ADMIN')")
public class ExportController {

    @Autowired private CurrentUserService currentUserService;
    @Autowired private StudentRepository studentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private FeeInvoiceRepository feeInvoiceRepository;

    @GetMapping("/students.csv")
    public ResponseEntity<byte[]> exportStudents(Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        StringBuilder sb = new StringBuilder();
        sb.append("FirstName,LastName,RollNumber,Grade,Section,ParentName,ParentPhone\n");
        if (tenantId != null) {
            for (Student s : studentRepository.findByTenantId(tenantId)) {
                ClassSection cs = s.getClassSection();
                Parent parent = s.getParents().stream().findFirst().orElse(null);
                String parentName = parent != null ? (parent.getFirstName() + " " + (parent.getLastName() == null ? "" : parent.getLastName())).trim() : "";
                String parentPhone = parent != null && parent.getPhoneNumber() != null ? parent.getPhoneNumber() : "";
                sb.append(row(
                        s.getFirstName(), s.getLastName(), s.getRollNumber(),
                        cs != null ? cs.getGradeName() : "", cs != null ? cs.getSectionName() : "",
                        parentName, parentPhone));
            }
        }
        return csv("students", sb.toString());
    }

    @GetMapping("/staff.csv")
    public ResponseEntity<byte[]> exportStaff(Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        StringBuilder sb = new StringBuilder();
        sb.append("FullName,Email,Role,Status\n");
        if (tenantId != null) {
            List<User> staff = userRepository.findByTenantIdAndRoleIn(tenantId,
                    Arrays.asList(UserRole.ADMIN, UserRole.PRINCIPAL, UserRole.TEACHER, UserRole.DRIVER));
            for (User u : staff) {
                sb.append(row(u.getFullName(), u.getEmail(), u.getRole().name(),
                        u.isActive() ? "Active" : "Pending"));
            }
        }
        return csv("staff", sb.toString());
    }

    @GetMapping("/fees.csv")
    public ResponseEntity<byte[]> exportFees(Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        StringBuilder sb = new StringBuilder();
        sb.append("StudentName,RollNumber,TotalAmount,AmountPaid,AmountDue,Status\n");
        if (tenantId != null) {
            List<FeeInvoice> invoices = feeInvoiceRepository.findByTenantId(tenantId);
            List<UUID> studentIds = invoices.stream().map(FeeInvoice::getStudentId).distinct().collect(Collectors.toList());
            var studentsById = studentRepository.findAllById(studentIds).stream()
                    .collect(Collectors.toMap(Student::getId, s -> s, (a, b) -> a));
            for (FeeInvoice inv : invoices) {
                Student s = studentsById.get(inv.getStudentId());
                String name = s != null ? (s.getFirstName() + " " + s.getLastName()) : "";
                String roll = s != null && s.getRollNumber() != null ? s.getRollNumber() : "";
                sb.append(row(name, roll,
                        str(inv.getTotalAmount()), str(inv.getAmountPaid()), str(inv.getAmountDue()),
                        inv.getStatus() != null ? inv.getStatus().name() : ""));
            }
        }
        return csv("fees", sb.toString());
    }

    private static String str(Object o) { return o == null ? "" : o.toString(); }

    /** Builds one CSV row from fields, escaping each, terminated with a newline. */
    private static String row(String... fields) {
        return Arrays.stream(fields).map(ExportController::escape).collect(Collectors.joining(",")) + "\n";
    }

    /** RFC4180 escaping: wrap in quotes and double internal quotes when the field needs it. */
    private static String escape(String field) {
        if (field == null) return "";
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    private static ResponseEntity<byte[]> csv(String name, String body) {
        String filename = "acadia-" + name + "-" + LocalDate.now() + ".csv";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDispositionFormData("attachment", filename);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}

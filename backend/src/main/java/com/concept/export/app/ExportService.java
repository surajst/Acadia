package com.concept.export.app;

import com.concept.shared.data.ClassSection;
import com.concept.management.FeeInvoice;
import com.concept.management.FeeInvoiceRepository;
import com.concept.shared.data.Parent;
import com.concept.shared.data.Student;
import com.concept.shared.data.StudentRepository;
import com.concept.user.CurrentUserService;
import com.concept.user.User;
import com.concept.user.UserRepository;
import com.concept.user.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application layer for admin data export (ADR 0001). Builds tenant-scoped CSV
 * bodies for students, staff, and fees, reading the shared management
 * repositories; every query is scoped to the caller's tenant so one school can
 * never export another's data. The interface layer only wraps the returned
 * string into an HTTP download.
 */
@Service
public class ExportService {

    private final CurrentUserService currentUserService;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final FeeInvoiceRepository feeInvoiceRepository;

    public ExportService(CurrentUserService currentUserService,
                         StudentRepository studentRepository,
                         UserRepository userRepository,
                         FeeInvoiceRepository feeInvoiceRepository) {
        this.currentUserService = currentUserService;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.feeInvoiceRepository = feeInvoiceRepository;
    }

    public String studentsCsv(Authentication authentication) {
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
        return sb.toString();
    }

    public String staffCsv(Authentication authentication) {
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
        return sb.toString();
    }

    public String feesCsv(Authentication authentication) {
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
        return sb.toString();
    }

    private static String str(Object o) { return o == null ? "" : o.toString(); }

    /** Builds one CSV row from fields, escaping each, terminated with a newline. */
    private static String row(String... fields) {
        return Arrays.stream(fields).map(ExportService::escape).collect(Collectors.joining(",")) + "\n";
    }

    /** RFC4180 escaping: wrap in quotes and double internal quotes when the field needs it. */
    private static String escape(String field) {
        if (field == null) return "";
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }
}

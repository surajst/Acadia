package com.schoolos.management;

import com.schoolos.user.CurrentUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
public class AdminFeeController {

    @Autowired
    private FeeInvoiceRepository feeInvoiceRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private FeeManagementService feeManagementService;

    @Autowired
    private CurrentUserService currentUserService;

    public static class EnrichedInvoiceDto {
        private final FeeInvoice invoice;
        private final Student student;

        public EnrichedInvoiceDto(FeeInvoice invoice, Student student) {
            this.invoice = invoice;
            this.student = student;
        }

        public FeeInvoice getInvoice() {
            return invoice;
        }

        public Student getStudent() {
            return student;
        }
    }

    @GetMapping("/web/admin/fees")
    public String showFeeDashboard(@RequestParam(value = "page", defaultValue = "0") int page,
                                   @RequestParam(value = "size", defaultValue = "20") int size,
                                   Model model, Authentication authentication) {
        
        // Enforce ROLE_ADMIN programmatically just as a fallback
        String role = "ADMIN";
        if (authentication != null) {
            boolean isAdmin = false;
            for (GrantedAuthority auth : authentication.getAuthorities()) {
                String authority = auth.getAuthority();
                if ("ROLE_ADMIN".equals(authority)) {
                    isAdmin = true;
                }
                if (authority.startsWith("ROLE_")) {
                    role = authority.substring(5);
                }
            }
            if (!isAdmin) {
                throw new RuntimeException("Access denied: Only administrators can view the financial ledger");
            }
        }
        model.addAttribute("currentUserRole", role);

        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);

        // 1. Calculate high-level financial summary metrics — scoped to this tenant only
        List<FeeInvoice> allInvoices = tenantId != null ? feeInvoiceRepository.findByTenantId(tenantId) : List.of();
        BigDecimal totalExpectedRevenue = BigDecimal.ZERO;
        BigDecimal totalCollected = BigDecimal.ZERO;
        BigDecimal totalOutstandingDeficit = BigDecimal.ZERO;

        for (FeeInvoice invoice : allInvoices) {
            totalExpectedRevenue = totalExpectedRevenue.add(invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO);
            totalCollected = totalCollected.add(invoice.getAmountPaid() != null ? invoice.getAmountPaid() : BigDecimal.ZERO);
            totalOutstandingDeficit = totalOutstandingDeficit.add(invoice.getAmountDue() != null ? invoice.getAmountDue() : BigDecimal.ZERO);
        }

        model.addAttribute("totalExpectedRevenue", totalExpectedRevenue);
        model.addAttribute("totalCollected", totalCollected);
        model.addAttribute("totalOutstandingDeficit", totalOutstandingDeficit);

        // 2. Fetch paginated invoices chunk, scoped to this tenant only
        Page<FeeInvoice> invoicePage = tenantId != null
                ? feeInvoiceRepository.findByTenantId(tenantId, PageRequest.of(page, size))
                : Page.empty();
        
        // 3. Batch resolve corresponding students to avoid N+1 queries
        List<UUID> studentIds = invoicePage.getContent().stream()
            .map(FeeInvoice::getStudentId)
            .collect(Collectors.toList());
        
        List<Student> students = studentRepository.findAllById(studentIds);
        Map<UUID, Student> studentMap = students.stream()
            .collect(Collectors.toMap(Student::getId, Function.identity()));

        List<EnrichedInvoiceDto> enrichedInvoices = invoicePage.getContent().stream()
            .map(invoice -> new EnrichedInvoiceDto(invoice, studentMap.get(invoice.getStudentId())))
            .collect(Collectors.toList());

        model.addAttribute("enrichedInvoices", enrichedInvoices);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", invoicePage.getTotalPages());
        model.addAttribute("totalItems", invoicePage.getTotalElements());
        model.addAttribute("pageSize", size);
        model.addAttribute("systemScope", "ADMIN_FINANCE");
        model.addAttribute("allStudents", tenantId != null ? studentRepository.findByTenantId(tenantId) : List.of());

        return "fee_management";
    }

    @PostMapping("/web/admin/fees/collect")
    public String collectPayment(@RequestParam("invoiceId") UUID invoiceId,
                                 @RequestParam("amount") BigDecimal amount,
                                 @RequestParam("paymentMode") String paymentMode,
                                 Authentication authentication) {
        
        if (authentication != null) {
            boolean isAdmin = false;
            for (GrantedAuthority auth : authentication.getAuthorities()) {
                if ("ROLE_ADMIN".equals(auth.getAuthority())) {
                    isAdmin = true;
                    break;
                }
            }
            if (!isAdmin) {
                throw new RuntimeException("Access denied: Only administrators can record dynamic payments");
            }
        }

        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        feeManagementService.recordPayment(invoiceId, amount, paymentMode, tenantId, authentication);
        return "redirect:/web/admin/fees?success=payment_recorded";
    }

    @PostMapping("/web/admin/fees/invoice/create")
    @PreAuthorize("hasRole('ADMIN')")
    public String createInvoice(@RequestParam("studentId") UUID studentId, Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        feeManagementService.createInvoiceForStudent(studentId, tenantId, authentication);
        return "redirect:/web/admin/fees?success=invoice_created";
    }

    @PostMapping("/api/admin/fees/{invoiceId}/waiver/request")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public Object requestWaiver(@PathVariable UUID invoiceId,
                                 @RequestParam("waiverAmount") BigDecimal waiverAmount,
                                 @RequestParam("reason") String reason,
                                 Authentication authentication) {
        try {
            UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
            FeeInvoice invoice = feeManagementService.requestWaiver(invoiceId, waiverAmount, reason, tenantId, authentication);
            return Map.of("status", "requested", "waiverStatus", invoice.getWaiverStatus());
        } catch (IllegalArgumentException e) {
            return Map.of("error", e.getMessage());
        }
    }

    @ExceptionHandler(Exception.class)
    public String handleException(Exception ex, Model model) {
        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("currentUserRole", "ADMIN");
        return "fee_management";
    }
}

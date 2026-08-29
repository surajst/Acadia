package com.concept.roster.web;

import com.concept.roster.app.StudentProfileNotFoundException;
import com.concept.roster.app.StudentProfileService;
import com.concept.roster.app.StudentProfileView;
import com.concept.recognition.app.RecognitionService;
import com.concept.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A child's profile, for a teacher holding a phone.
 *
 * <p>This exists because the information a preschool most urgently needs --
 * what this child is allergic to, who is allowed to collect them -- was
 * reachable only from the web console. The moment it is wanted is at the gate
 * or in the room, where nobody has a laptop open.
 *
 * <p>Everything is flattened to a map here rather than returning the view
 * record directly: the app reads plain JSON fields, and the profile record
 * carries things a teacher has no business seeing on a phone, such as sign-in
 * usernames.
 */
@RestController
@RequestMapping("/api/mobile/teacher")
public class MobileStudentProfileController {

    private final StudentProfileService studentProfileService;
    private final RecognitionService recognitionService;
    private final TenantContext tenantContext;

    public MobileStudentProfileController(StudentProfileService studentProfileService,
                                          RecognitionService recognitionService,
                                          TenantContext tenantContext) {
        this.studentProfileService = studentProfileService;
        this.recognitionService = recognitionService;
        this.tenantContext = tenantContext;
    }

    /**
     * One child, as much as a teacher needs and no more.
     *
     * <p>404 for a child in another school rather than 403: a teacher probing
     * ids should not be able to tell the difference between "not yours" and
     * "does not exist".
     */
    @GetMapping("/student/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'PRINCIPAL')")
    public ResponseEntity<?> student(@PathVariable("id") UUID id, Authentication authentication) {
        UUID tenantId = tenantContext.getTenantId().orElse(null);
        StudentProfileView view;
        try {
            view = studentProfileService.getProfile(id, tenantId);
        } catch (StudentProfileNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", "Student not found."));
        }

        Map<String, Object> out = new HashMap<>();
        out.put("studentId", view.studentId());
        out.put("firstName", view.firstName());
        out.put("lastName", view.lastName());
        out.put("rollNumber", view.rollNumber());
        out.put("gradeName", view.gradeName());
        out.put("sectionName", view.sectionName());
        out.put("ageYears", view.ageYears());
        out.put("dateOfBirth", view.dateOfBirth());

        // The three fields this endpoint exists for.
        out.put("medicalNotes", view.medicalNotes());
        out.put("emergencyContactName", view.emergencyContactName());
        out.put("emergencyContactPhone", view.emergencyContactPhone());
        out.put("pickupContacts", view.pickupContacts());

        out.put("primaryGuardian", view.primaryGuardian());
        out.put("guardianPhone", view.guardianPhone());
        out.put("attendancePercentage", view.attendancePercentage());
        out.put("presentCount", view.presentCount());
        out.put("absentCount", view.absentCount());
        out.put("schoolXp", view.schoolXp());
        out.put("awards", recognitionService.history(id, tenantId));
        return ResponseEntity.ok(out);
    }

    /** What a teacher may recognise a child for, for rendering the picker. */
    @GetMapping("/badges")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'PRINCIPAL')")
    public ResponseEntity<?> badges() {
        List<Map<String, Object>> out = recognitionService.catalogue().stream()
                .map(b -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("code", b.getCode());
                    m.put("label", b.getLabel());
                    m.put("emoji", b.getEmoji());
                    m.put("points", b.getPoints());
                    m.put("suggestion", b.getSuggestion());
                    return m;
                })
                .toList();
        return ResponseEntity.ok(out);
    }

    /**
     * Recognises a child from the phone. Same service, same rules, same audit
     * trail as the web -- the surface differs, the meaning does not.
     */
    @PostMapping("/student/{id}/award")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'PRINCIPAL')")
    public ResponseEntity<?> award(@PathVariable("id") UUID id,
                                   @RequestBody Map<String, String> body,
                                   Authentication authentication) {
        try {
            RecognitionService.AwardView view = recognitionService.award(
                    id, body.get("badgeCode"), body.get("reason"),
                    tenantContext.getTenantId().orElse(null), authentication);
            return ResponseEntity.ok(view);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

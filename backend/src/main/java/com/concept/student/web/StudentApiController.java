package com.concept.student.web;

import com.concept.student.app.StudentException;
import com.concept.student.app.StudentPortalPageService;
import com.concept.student.app.StudentPortalPageService.RedeemResult;
import com.concept.student.app.StudentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Interface layer for the student JSON API — portal actions (complete skill,
 * claim quest, confirm reward) and curriculum progress. Thin binding over
 * {@link StudentService}; every ownership check lives in the service (ADR 0001).
 */
@RestController
@RequestMapping("/api/student")
@PreAuthorize("hasRole('STUDENT')")
public class StudentApiController {

    private final StudentService studentService;
    private final StudentPortalPageService studentPortalPageService;

    public StudentApiController(StudentService studentService,
                                StudentPortalPageService studentPortalPageService) {
        this.studentService = studentService;
        this.studentPortalPageService = studentPortalPageService;
    }

    @PostMapping("/complete-skill/{id}")
    public ResponseEntity<?> completeSkill(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(studentService.completeSkill(id, authentication));
    }

    @PostMapping("/claim-quest/{id}")
    public ResponseEntity<?> claimQuest(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(studentService.claimQuestApi(id, authentication));
    }

    @PostMapping("/confirm-reward-received/{id}")
    public ResponseEntity<?> confirmRewardReceived(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(studentService.confirmRewardReceived(id, authentication));
    }

    @GetMapping("/progress")
    public ResponseEntity<?> progress(Authentication authentication) {
        return ResponseEntity.ok(studentService.progress(authentication));
    }

    @PostMapping("/progress/complete")
    public ResponseEntity<?> markComplete(@RequestBody Map<String, String> body, Authentication authentication) {
        return ResponseEntity.ok(studentService.markProgressComplete(body.get("curriculumId"), authentication));
    }

    /**
     * Spending XP from the app. Redeeming existed only as a Thymeleaf form post
     * that answered with a redirect, so the phone had no way to reach it -- the
     * marketplace was visible on the portal and nowhere else.
     */
    @PostMapping("/rewards/{id}/redeem")
    public ResponseEntity<?> redeemReward(@PathVariable UUID id, Authentication authentication) {
        return redeemResponse(studentPortalPageService.redeemReward(id, authentication));
    }

    @PostMapping("/parent-rewards/{id}/redeem")
    public ResponseEntity<?> redeemParentReward(@PathVariable UUID id, Authentication authentication) {
        return redeemResponse(studentPortalPageService.redeemParentReward(id, authentication));
    }

    private ResponseEntity<?> redeemResponse(RedeemResult outcome) {
        return switch (outcome) {
            case SUCCESS -> ResponseEntity.ok(Map.of("status", "redeemed"));
            case INSUFFICIENT_XP -> ResponseEntity.badRequest()
                    .body(Map.of("error", "Not enough XP for this one yet."));
            case NO_LINKED_PARENT -> ResponseEntity.badRequest()
                    .body(Map.of("error", "This reward needs a parent linked to your account."));
        };
    }

    @ExceptionHandler(StudentException.class)
    public ResponseEntity<?> handle(StudentException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnexpected(Exception e) {
        return ResponseEntity.internalServerError().body(Map.of("error", String.valueOf(e.getMessage())));
    }
}

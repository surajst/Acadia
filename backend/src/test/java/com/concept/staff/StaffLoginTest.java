package com.concept.staff;

import com.concept.common.EmailDeliveryService;
import com.concept.staff.app.StaffInvite;
import com.concept.staff.app.StaffService;
import com.concept.tenant.AcademicYear;
import com.concept.tenant.AcademicYearRepository;
import com.concept.tenant.Tenant;
import com.concept.tenant.TenantRepository;
import com.concept.user.User;
import com.concept.user.UserRepository;
import com.concept.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;

/**
 * An invite that produces a credential nobody can sign in with is not an
 * invite. Two separate faults made that the actual behaviour, and both were
 * invisible from the admin console, which reported the account as "Active":
 *
 * <ul>
 *   <li>the account was created PENDING, and {@code CustomUserDetailsService}
 *       refuses PENDING — so the sign-in failed as "Invalid username or
 *       password", naming the credential rather than the approval; and</li>
 *   <li>the lookup is by exact email, so the same address in a different case
 *       was simply a different, missing account.</li>
 * </ul>
 *
 * <p>These drive the real form-login endpoint rather than the service, because
 * that is where both faults surfaced and neither was visible from a unit test
 * of the invite itself.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class StaffLoginTest {

    @Autowired private StaffService staffService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MockMvc mockMvc;

    @MockBean private EmailDeliveryService emailDeliveryService;

    private UUID tenantId;
    private UUID yearId;

    @BeforeEach
    void setup() {
        when(emailDeliveryService.send(any(), any(), any()))
                .thenReturn(EmailDeliveryService.EmailResult.sent());

        tenantId = UUID.randomUUID();
        yearId = UUID.randomUUID();

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName("Demo SSC");
        tenant.setSubdomain("demo-" + tenantId.toString().substring(0, 8));
        tenant.setActive(true);
        tenant.setCreatedAt(Instant.now());
        tenantRepository.saveAndFlush(tenant);

        AcademicYear year = new AcademicYear();
        year.setId(yearId);
        year.setTenantId(tenantId);
        year.setName("2026-27");
        year.setStartDate(LocalDate.of(2026, 4, 1));
        year.setEndDate(LocalDate.of(2027, 3, 31));
        year.setCurrent(true);
        academicYearRepository.saveAndFlush(year);
    }

    private StaffInvite invite(String email) {
        return staffService.inviteStaff("Neha Test", email, UserRole.TEACHER,
                "Demo SSC", tenantId, yearId, null);
    }

    /**
     * P0-1. The admin is shown a temporary password; it has to work. The
     * invite is performed by an ADMIN or PRINCIPAL — the same roles that would
     * approve the account — so there is nobody left to wait for.
     */
    @Test
    void invitedTeacherCanSignInWithTheTemporaryPasswordTheAdminWasShown() throws Exception {
        String email = "neha.teacher-" + UUID.randomUUID() + "@example.com";
        StaffInvite invited = invite(email);

        mockMvc.perform(formLogin("/login").user(email).password(invited.temporaryPassword()))
                .andExpect(authenticated().withUsername(email));
    }

    /** P0-2. The address is the same address whatever case it is typed in. */
    @Test
    void signInIgnoresTheCaseOfTheEmail() throws Exception {
        String email = "priya.teacher-" + UUID.randomUUID() + "@example.com";
        StaffInvite invited = invite(email);

        mockMvc.perform(formLogin("/login")
                        .user(email.toUpperCase())
                        .password(invited.temporaryPassword()))
                .andExpect(authenticated());
    }

    /**
     * P0-2, storage side. Normalising only at lookup would leave the console
     * and the receipts showing whatever case was typed, and a second invite for
     * the same person in another case would be accepted as a new account.
     */
    @Test
    void theStoredEmailIsNormalised() {
        String email = "  Suraj10-" + UUID.randomUUID() + "@Gmail.com  ";
        StaffInvite invited = invite(email);

        User saved = userRepository.findById(invited.id()).orElseThrow();
        assertEquals(email.trim().toLowerCase(), saved.getEmail());
        assertTrue(userRepository.existsByEmail(email.trim().toLowerCase()));
    }

    /** A wrong password must still be refused — the fix must not open the door. */
    @Test
    void aWrongPasswordIsStillRefused() throws Exception {
        String email = "wrong-" + UUID.randomUUID() + "@example.com";
        invite(email);

        mockMvc.perform(formLogin("/login").user(email).password("not-the-password"))
                .andExpect(unauthenticated());
    }

    /**
     * An account an admin deactivates must stay out, so that the approval
     * change does not quietly become "every account is always enabled".
     */
    @Test
    void aDeactivatedAccountIsRefused() throws Exception {
        String email = "gone-" + UUID.randomUUID() + "@example.com";
        StaffInvite invited = invite(email);

        User staff = userRepository.findById(invited.id()).orElseThrow();
        staff.setActive(false);
        userRepository.saveAndFlush(staff);

        mockMvc.perform(formLogin("/login").user(email).password(invited.temporaryPassword()))
                .andExpect(unauthenticated());
    }
}

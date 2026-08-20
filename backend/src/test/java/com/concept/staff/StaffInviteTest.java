package com.concept.staff;

import com.concept.common.EmailDeliveryService;
import com.concept.staff.app.StaffInvite;
import com.concept.staff.app.StaffService;
import com.concept.tenant.AcademicYear;
import com.concept.tenant.AcademicYearRepository;
import com.concept.tenant.Tenant;
import com.concept.tenant.TenantRepository;
import com.concept.user.UserRepository;
import com.concept.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Staff invites used to end at "account created" — the temporary password was
 * generated in browser JavaScript and the admin relayed it by hand. These pin
 * the two things that changed: the credential is the server's to make, and a
 * failed send is reported rather than swallowed.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
public class StaffInviteTest {

    @Autowired private StaffService staffService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private UserRepository userRepository;

    @MockBean private EmailDeliveryService emailDeliveryService;

    private UUID tenantId;
    private UUID yearId;

    @BeforeEach
    public void setup() {
        tenantId = UUID.randomUUID();
        yearId = UUID.randomUUID();

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName("Greenwood High");
        tenant.setSubdomain("gw-" + tenantId.toString().substring(0, 8));
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

    private String uniqueEmail() {
        return "teacher-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    public void inviteStaff_generatesThePasswordServerSide_andEmailsIt() {
        when(emailDeliveryService.send(any(), any(), any()))
                .thenReturn(EmailDeliveryService.EmailResult.sent());

        String email = uniqueEmail();
        StaffInvite invite = staffService.inviteStaff("Asha Rao", email, UserRole.TEACHER,
                "Greenwood High", tenantId, yearId, null);

        assertNotNull(invite.temporaryPassword(), "the server must issue the credential");
        assertTrue(invite.temporaryPassword().length() >= 10);
        assertTrue(invite.emailed());

        // The credential must actually be in the message, otherwise the
        // recipient has an account and no way into it.
        verify(emailDeliveryService).send(eq(email), any(),
                org.mockito.ArgumentMatchers.contains(invite.temporaryPassword()));
    }

    /**
     * The account is created before the email is attempted. A failed send must
     * not roll it back, and must not be reported as success — otherwise an
     * admin is told a teacher was emailed when nobody was, and the teacher
     * waits for a message that does not exist.
     */
    @Test
    public void inviteStaff_whenEmailFails_stillCreatesTheAccountAndSaysSo() {
        when(emailDeliveryService.send(any(), any(), any()))
                .thenReturn(EmailDeliveryService.EmailResult.failed("Sender domain not verified"));

        String email = uniqueEmail();
        StaffInvite invite = staffService.inviteStaff("Vikram Patel", email, UserRole.TEACHER,
                "Greenwood High", tenantId, yearId, null);

        assertFalse(invite.emailed(), "a failed send must not be reported as delivered");
        assertEquals("Sender domain not verified", invite.emailDetail());
        assertNotNull(invite.temporaryPassword(),
                "the admin needs the credential precisely because the email did not go");
        assertTrue(userRepository.existsByEmail(email), "the account must survive a failed send");
    }

    @Test
    public void inviteStaff_duplicateEmail_isRefusedAndSendsNothing() {
        when(emailDeliveryService.send(any(), any(), any()))
                .thenReturn(EmailDeliveryService.EmailResult.sent());

        String email = uniqueEmail();
        staffService.inviteStaff("First Person", email, UserRole.TEACHER, "Greenwood High",
                tenantId, yearId, null);
        reset(emailDeliveryService);

        assertThrows(IllegalArgumentException.class, () ->
                staffService.inviteStaff("Second Person", email, UserRole.TEACHER, "Greenwood High",
                        tenantId, yearId, null));
        verify(emailDeliveryService, never()).send(any(), any(), any());
    }

    @Test
    public void inviteStaff_issuesADifferentPasswordEachTime() {
        when(emailDeliveryService.send(any(), any(), any()))
                .thenReturn(EmailDeliveryService.EmailResult.sent());

        String one = staffService.inviteStaff("A One", uniqueEmail(), UserRole.TEACHER,
                "Greenwood High", tenantId, yearId, null).temporaryPassword();
        String two = staffService.inviteStaff("B Two", uniqueEmail(), UserRole.TEACHER,
                "Greenwood High", tenantId, yearId, null).temporaryPassword();

        assertNotEquals(one, two);
    }
}

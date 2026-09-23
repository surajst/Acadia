package com.concept.common;

import com.concept.attendance.app.AttendanceService;
import com.concept.attendance.app.MarkAttendanceCommand;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.shared.data.Parent;
import com.concept.shared.data.ParentRepository;
import com.concept.shared.data.Student;
import com.concept.shared.data.StudentRepository;
import com.concept.tenant.AcademicYear;
import com.concept.tenant.AcademicYearRepository;
import com.concept.tenant.Tenant;
import com.concept.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Marking a child absent messages their guardian straight away. The manual
 * Register Student form used to accept "abc123" as a phone number, so some of
 * those guardians have nothing dialable on file -- and a provider handed a
 * value like that either errors or, worse, normalises it into somebody else's
 * number.
 *
 * <p>The seam is mocked here deliberately: what matters is whether anything is
 * handed to a provider at all, not what the console stub does with it.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class NotificationSafetyTest {

    @Autowired private AttendanceService attendanceService;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ParentRepository parentRepository;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;

    @MockBean private NotificationDeliveryService notificationDeliveryService;

    private UUID tenantId;
    private UUID yearId;
    private ClassSection section;

    @BeforeEach
    void setup() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Demo SSC");
        tenant.setSubdomain("ns-" + UUID.randomUUID());
        tenant.setActive(true);
        tenant.setCreatedAt(Instant.now());
        tenantId = tenantRepository.saveAndFlush(tenant).getId();

        AcademicYear year = new AcademicYear();
        year.setId(UUID.randomUUID());
        year.setTenantId(tenantId);
        year.setName("2026-27");
        year.setStartDate(LocalDate.of(2026, 4, 1));
        year.setEndDate(LocalDate.of(2027, 3, 31));
        year.setCurrent(true);
        yearId = academicYearRepository.saveAndFlush(year).getId();

        section = new ClassSection();
        section.setId(UUID.randomUUID());
        section.setTenantId(tenantId);
        section.setAcademicYearId(yearId);
        section.setGradeName("Grade 6");
        section.setSectionName("A");
        section = classSectionRepository.saveAndFlush(section);
    }

    private Student studentWithGuardianPhone(String phone) {
        Parent parent = new Parent();
        parent.setId(UUID.randomUUID());
        parent.setTenantId(tenantId);
        parent.setAcademicYearId(yearId);
        parent.setFirstName("Rakesh");
        parent.setLastName("Verma");
        parent.setPhoneNumber(phone);
        parent = parentRepository.saveAndFlush(parent);

        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setTenantId(tenantId);
        student.setAcademicYearId(yearId);
        student.setFirstName("Aarav");
        student.setLastName("Verma");
        student.setClassSection(section);
        student = studentRepository.saveAndFlush(student);
        student.getParents().add(parent);
        return studentRepository.saveAndFlush(student);
    }

    private void markAbsent(Student student) {
        attendanceService.mark(
                new MarkAttendanceCommand(tenantId, List.of(student.getId()), List.of("ABSENT")), null);
    }

    @Test
    void nothingIsDispatchedToAGuardianWithAnUnusablePhoneNumber() {
        markAbsent(studentWithGuardianPhone("abc123"));
        verify(notificationDeliveryService, never()).send(any(), any());
    }

    @Test
    void nothingIsDispatchedWhenTheGuardianHasNoNumberAtAll() {
        markAbsent(studentWithGuardianPhone(null));
        verify(notificationDeliveryService, never()).send(any(), any());
    }

    /** The guard must not silence the alert it exists to protect. */
    @Test
    void aGuardianWithARealNumberIsStillAlerted() {
        markAbsent(studentWithGuardianPhone("+91 9876543210"));
        verify(notificationDeliveryService, times(1)).send(eq("+91 9876543210"), any());
    }

    @Test
    void aChildMarkedPresentTriggersNothing() {
        Student student = studentWithGuardianPhone("+91 9876543210");
        attendanceService.mark(
                new MarkAttendanceCommand(tenantId, List.of(student.getId()), List.of("PRESENT")), null);
        verify(notificationDeliveryService, never()).send(any(), any());
    }

    /** Email is not configured in production, and the console stub says so. */
    @Test
    void theConsoleEmailStubReportsItselfUnconfigured() {
        assertFalse(new ConsoleEmailDeliveryService().isConfigured(),
                "an admin has to be told before they invite anybody");
    }
}

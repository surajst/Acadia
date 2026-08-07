package com.concept.roster.app;

import com.concept.management.ClassSection;
import com.concept.management.SchoolClass;
import com.concept.management.Student;
import com.concept.academics.StudentMetric;

import java.util.List;
import java.util.UUID;

/**
 * Everything the student-profile page needs, assembled by the application layer
 * and handed to the controller to place on the view model. The controller adds
 * only presentation-shell attributes (role, static empty states).
 *
 * <p>NOTE: this first slice still bundles the {@link Student}/{@link StudentMetric}
 * entities because the existing {@code student_profile.html} template is coupled
 * to them. Fully replacing them with flat view fields is a follow-up once the
 * wider roster domain (entities → roster.data) is migrated; the security and
 * layering wins (tenant-scoped fetch, single decision point) already hold.
 */
public record StudentProfileView(
        Student student,
        long presentCount,
        long absentCount,
        int attendancePercentage,
        StudentMetric studentMetrics,
        List<ClassSection> availableClassesMenu,
        String primaryGuardian,
        String guardianPhone,
        UUID primaryGuardianId,
        String primaryGuardianFirstName,
        String primaryGuardianLastName,
        int guardianCount,
        int householdStreak,
        List<SchoolClass> classList,
        UUID currentSchoolClassId
) {}

package com.concept.roster.app;

import java.util.List;
import java.util.UUID;

/**
 * A flat, presentation-ready summary of a student profile. Contains only plain
 * values and small view records — no JPA entities — so nothing about how the
 * data is stored leaks into the interface layer or the template (ADR 0001).
 */
public record StudentProfileView(
        UUID studentId,
        String firstName,
        String lastName,
        String rollNumber,
        String gradeName,
        String sectionName,
        long presentCount,
        long absentCount,
        int attendancePercentage,
        int schoolXp,
        int parentXp,
        int activeStreak,
        String primaryGuardian,
        String guardianPhone,
        UUID primaryGuardianId,
        String primaryGuardianFirstName,
        String primaryGuardianLastName,
        int guardianCount,
        int householdStreak,
        List<ClassOption> classList,
        UUID currentSchoolClassId
) {}

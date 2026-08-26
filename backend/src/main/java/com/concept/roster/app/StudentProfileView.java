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
        UUID currentClassSectionId,
        /**
         * Sign-in usernames, shown on the profile so an admin can tell a family
         * how to log in without having to reset the password to find out. Only
         * the username: passwords are stored hashed and are unrecoverable by
         * design, which is why the profile offers "reset" rather than "reveal".
         */
        String studentLoginUsername,
        String guardianLoginUsername,
        java.time.LocalDate dateOfBirth,
        Integer ageYears,
        String medicalNotes,
        String emergencyContactName,
        String emergencyContactPhone,
        java.util.List<PickupContactService.Row> pickupContacts
) {}

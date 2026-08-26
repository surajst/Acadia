package com.concept.tenant;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * School type decides vocabulary and visible modules. The safety property is
 * that adding it changed nothing for schools that already exist: every tenant
 * predating the column reads as SECONDARY and keeps the words and modules it
 * had.
 */
class SchoolTypeTest {

    @Test
    void aTenantWithNoTypeSetReadsAsAConventionalSchool() {
        Tenant tenant = new Tenant();
        assertEquals(SchoolType.SECONDARY, tenant.getSchoolType(),
                "every tenant predating this column must keep its current behaviour");
    }

    @Test
    void aPreschoolSpeaksOfLevelsAndActivities() {
        assertEquals("Level", SchoolType.PRESCHOOL.getLevelSingular());
        assertEquals("Levels", SchoolType.PRESCHOOL.getLevelPlural());
        assertEquals("Activity Area", SchoolType.PRESCHOOL.getActivitySingular());
    }

    @Test
    void everyOtherTypeSpeaksOfClassesAndSubjects() {
        for (SchoolType type : List.of(SchoolType.PRIMARY, SchoolType.SECONDARY, SchoolType.K10)) {
            assertEquals("Class", type.getLevelSingular(), type + " should say Class");
            assertEquals("Subject", type.getActivitySingular(), type + " should say Subject");
        }
    }

    @Test
    void aPreschoolHidesTheModulesThatAssumeALiterateChild() {
        assertFalse(SchoolType.PRESCHOOL.hasHomeworkAndQuests());
        assertFalse(SchoolType.PRESCHOOL.hasSyllabus());
        assertFalse(SchoolType.PRESCHOOL.hasExamScores());
        assertFalse(SchoolType.PRESCHOOL.hasNumberedGrades());
    }

    @Test
    void everyOtherTypeKeepsThoseModules() {
        // The gating must not have quietly switched anything off for the
        // schools already using the system.
        for (SchoolType type : List.of(SchoolType.PRIMARY, SchoolType.SECONDARY, SchoolType.K10)) {
            assertTrue(type.hasHomeworkAndQuests(), type + " must keep quests");
            assertTrue(type.hasSyllabus(), type + " must keep the syllabus");
            assertTrue(type.hasExamScores(), type + " must keep exam scores");
            assertTrue(type.hasNumberedGrades(), type + " must keep numbered grades");
        }
    }

    @Test
    void preschoolLevelsAreTheOnesAPreschoolActuallyUses() {
        assertEquals(List.of("Pre-Nursery", "Nursery", "LKG", "UKG"),
                Arrays.asList(SchoolType.PRESCHOOL.defaultLevels()));
    }

    @Test
    void preschoolActivitiesAreAreasOfPlayNotAcademicSubjects() {
        List<String> activities = Arrays.asList(SchoolType.PRESCHOOL.defaultActivities());
        assertTrue(activities.contains("Circle Time"));
        assertTrue(activities.contains("Outdoor Play"));
        assertFalse(activities.contains("Mathematics"),
                "a preschool is not taught Mathematics as a school subject");
    }

    @Test
    void everyTypeSuppliesLevelsAndActivities() {
        // A type with no defaults would onboard a school into an empty app.
        for (SchoolType type : SchoolType.values()) {
            assertTrue(type.defaultLevels().length > 0, type + " has no default levels");
            assertTrue(type.defaultActivities().length > 0, type + " has no default activities");
        }
    }

    @Test
    void noPreschoolLevelNameContainsADigit() {
        // GradeLevel reads a year out of the label and answers UNKNOWN when it
        // cannot. That is correct for these names, and this pins the assumption:
        // if someone adds "Class 1" to the preschool list, the two features
        // disagree about what a level is.
        for (String level : SchoolType.PRESCHOOL.defaultLevels()) {
            assertFalse(level.matches(".*\\d.*"),
                    level + " has a digit in it, which contradicts hasNumberedGrades() == false");
        }
    }
}

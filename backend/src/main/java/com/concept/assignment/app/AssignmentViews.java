package com.concept.assignment.app;

import java.util.List;
import java.util.UUID;

/**
 * Flat view records for the assignment web page and JSON API — the web layer
 * receives these, never JPA entities (ADR 0001). Thymeleaf resolves the record
 * accessors directly.
 */
public final class AssignmentViews {

    private AssignmentViews() {}

    /** A selectable teacher in the assign form / filter dropdown. */
    public record TeacherOption(UUID id, String fullName) {}

    /** A selectable class section in the assign form. */
    public record SectionOption(UUID id, String gradeName, String sectionName) {}

    /** A row in the current-assignments table. */
    public record AssignmentRow(UUID id, String className, String subjectName, boolean homeClass) {}

    /** The full model backing GET /web/admin/assignments. */
    public record AssignmentsPage(String currentUserRole,
                                  List<TeacherOption> teachers,
                                  List<SectionOption> sections,
                                  List<AssignmentRow> assignments,
                                  UUID selectedTeacherId) {}
}

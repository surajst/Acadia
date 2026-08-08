package com.concept.dashboard.app;

import java.util.List;
import java.util.Map;

/**
 * Everything the unified admin/teacher roster dashboard renders, flat: the
 * paged roster, the grade-filter options, the headline attendance stats, the
 * pagination totals, and (PRINCIPAL only) the school-wide rollups.
 */
public record RosterDashboardView(List<StudentRow> roster,
                                  List<String> allGradeNames,
                                  long totalStudents,
                                  long activeAbsences,
                                  int attendancePercentage,
                                  int totalPages,
                                  long totalRosterItems,
                                  Map<String, Object> schoolProgress,
                                  Map<String, Object> feeSummary) {}

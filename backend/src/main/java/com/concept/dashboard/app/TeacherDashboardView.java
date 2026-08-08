package com.concept.dashboard.app;

import java.util.List;

/** The teacher dashboard's two flat verification queues. */
public record TeacherDashboardView(List<TeacherTaskRow> pendingSubmissions,
                                   List<TeacherProgressRow> pendingProgress) {}

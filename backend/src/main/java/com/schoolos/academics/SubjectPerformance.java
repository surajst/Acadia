package com.schoolos.academics;

import com.schoolos.management.SubjectType;

import java.util.List;

public record SubjectPerformance(SubjectType subjectType, double averagePercentage, List<Double> trend) {
}

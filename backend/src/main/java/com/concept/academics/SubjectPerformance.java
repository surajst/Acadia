package com.concept.academics;

import java.util.List;

public record SubjectPerformance(String subjectCode, double averagePercentage, List<Double> trend) {
}

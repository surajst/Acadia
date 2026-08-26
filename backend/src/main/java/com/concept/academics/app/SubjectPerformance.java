package com.concept.academics.app;

import java.util.List;

public record SubjectPerformance(String subjectCode, double averagePercentage, List<Double> trend) {
}

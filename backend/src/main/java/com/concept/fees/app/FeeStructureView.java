package com.concept.fees.app;

import com.concept.fees.data.FeeStructure;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Flat row for the fee settings screen. Keeps the entity out of the interface
 * layer (ADR 0001) and carries the total pre-computed, so the template does not
 * do arithmetic that has to stay in step with invoicing.
 */
public record FeeStructureView(UUID id, String gradeLevel, BigDecimal tuitionFee,
                               BigDecimal termFee, BigDecimal total) {

    public static List<FeeStructureView> of(List<FeeStructure> structures) {
        return structures.stream().map(FeeStructureView::of).toList();
    }

    public static FeeStructureView of(FeeStructure s) {
        return new FeeStructureView(s.getId(), s.getGradeLevel(), s.getTuitionFee(), s.getTermFee(),
                s.getTuitionFee().add(s.getTermFee()));
    }
}

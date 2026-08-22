package com.concept.fees.app;

import com.concept.fees.data.FeePlan;
import com.concept.fees.data.FeePlanInstalment;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Flat view of a plan and its schedule for the settings screen. Keeps entities
 * out of the interface layer (ADR 0001).
 */
public record FeePlanView(UUID id, String gradeLevel, BigDecimal annualAmount,
                          List<InstalmentRow> instalments) {

    public record InstalmentRow(int sequenceNumber, String label, BigDecimal amount, int dueOffsetDays) {}

    public static List<FeePlanView> of(List<FeePlan> plans,
                                       Function<UUID, List<FeePlanInstalment>> instalmentsOf) {
        return plans.stream()
                .map(plan -> new FeePlanView(
                        plan.getId(),
                        plan.getGradeLevel(),
                        plan.getAnnualAmount(),
                        instalmentsOf.apply(plan.getId()).stream()
                                .map(i -> new InstalmentRow(i.getSequenceNumber(), i.getLabel(),
                                        i.getAmount(), i.getDueOffsetDays()))
                                .toList()))
                .toList();
    }
}

package com.schoolos.parentapp;

import java.time.LocalDate;

public record DateRange(LocalDate start, LocalDate end) {
    public static DateRange lastDays(int days) {
        LocalDate end = LocalDate.now();
        return new DateRange(end.minusDays(days), end);
    }
}

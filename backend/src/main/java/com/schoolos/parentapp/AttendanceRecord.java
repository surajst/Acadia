package com.schoolos.parentapp;

import java.time.LocalDate;

public record AttendanceRecord(LocalDate date, String status) {
}

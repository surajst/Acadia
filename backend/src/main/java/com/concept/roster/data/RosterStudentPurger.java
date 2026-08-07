package com.concept.roster.data;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Data-layer helper that cascade-deletes a student's dependent rows before the
 * student itself. Kept in the data layer so raw storage access never leaks into
 * the application service (ADR 0001). The caller is responsible for the tenant
 * check — this only runs once the student has been resolved tenant-scoped.
 */
@Component
public class RosterStudentPurger {

    private final JdbcTemplate jdbcTemplate;

    public RosterStudentPurger(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Delete all rows that reference the student, then the student row. */
    public void purge(UUID studentId) {
        jdbcTemplate.update("DELETE FROM fee_transactions WHERE invoice_id IN (SELECT id FROM fee_invoices WHERE student_id = ?)", studentId);
        jdbcTemplate.update("DELETE FROM fee_invoices WHERE student_id = ?", studentId);
        jdbcTemplate.update("DELETE FROM student_metrics WHERE student_id = ?", studentId);
        jdbcTemplate.update("DELETE FROM academic_submissions WHERE student_id = ?", studentId);
        jdbcTemplate.update("DELETE FROM attendance WHERE student_id = ?", studentId);
        jdbcTemplate.update("DELETE FROM parent_quests WHERE student_id = ?", studentId);
        jdbcTemplate.update("DELETE FROM parent_rewards WHERE student_id = ?", studentId);
        jdbcTemplate.update("DELETE FROM student_parents WHERE student_id = ?", studentId);
        jdbcTemplate.update("DELETE FROM students WHERE id = ?", studentId);
    }
}

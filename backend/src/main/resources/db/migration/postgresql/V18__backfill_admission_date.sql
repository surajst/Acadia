-- Give existing students the admission date nothing ever recorded.
--
-- students.admission_date has been on the table since V5 and no code path
-- wrote it, so InvoiceScheduleService always fell through to the academic
-- year's start. A child admitted in September was billed from June and was
-- overdue on their first day -- which contradicts the Fee plans page, which
-- promises instalment offsets counted "from each student's start date".
--
-- The audit log is the only record of when a student was actually added, and
-- STUDENT_ADDED carries their id, so it is the source here. Students with no
-- such row (seeded, or imported before auditing covered it) keep NULL and
-- behave exactly as they do today -- billing from the year start.
--
-- This corrects future invoice runs only. Invoices already raised keep the due
-- dates they were given; those have to be re-raised to pick this up.
UPDATE students s
   SET admission_date = (
        SELECT CAST(MIN(a.created_at) AS date)
          FROM audit_logs a
         WHERE a.entity_type = 'Student'
           AND a.action = 'STUDENT_ADDED'
           AND a.entity_id = s.id
   )
 WHERE s.admission_date IS NULL
   AND EXISTS (
        SELECT 1
          FROM audit_logs a
         WHERE a.entity_type = 'Student'
           AND a.action = 'STUDENT_ADDED'
           AND a.entity_id = s.id
   );

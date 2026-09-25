-- Give a student an admission date when V18 could not, and stop a mid-year
-- joiner being billed from the start of the year.
--
-- The reported symptom: Riya Singh, created 2026-09-23, shows Term 1 "overdue
-- 01 Jun 2026", and "Fix due dates" reports that every due date already counts
-- from her own start date. Both statements are true at once, which is what makes
-- it confusing.
--
-- WHY. Nothing ever writes admission_date = the year start. The two code paths
-- that create a student (StudentAdminService, RosterImportService) both write
-- LocalDate.now(), and they have done since 2026-09-23. What happens instead is
-- that admission_date is left NULL, and InvoiceScheduleService.billingStartFor
-- falls back to the academic year's start when it is NULL. So the due dates are
-- genuinely consistent with her billing start -- the billing start is just
-- derived from a year rather than from her.
--
-- That matters for the fix: a migration keyed on `admission_date = year start`
-- would match no rows at all and report exactly the "nothing changed" she is
-- already seeing. This handles NULL as the primary case.
--
-- WHY V18 MISSED HER. V18 read MIN(audit_logs.created_at) for action
-- 'STUDENT_ADDED' only, and left NULL where there was no such row -- a student
-- imported before auditing covered that path, or created by a seeder, or created
-- with no authenticated actor, has none. But audit_logs holds plenty of other
-- rows scoped to a student: STUDENT_UPDATED, ROSTER_BULK_IMPORT, XP_AWARDED,
-- PICKUP_CONTACT_ADDED, FEE_SCHEDULE_GENERATED. The earliest of *any* of them is
-- a sound lower bound for when the student started existing here, and it is
-- always a better answer than the start of the academic year.
--
-- Deliberately NOT edited into V18: it has already run everywhere, and Flyway
-- would refuse the changed checksum.
--
-- Two guards on the update:
--   * only where admission_date is NULL or exactly the academic year's start --
--     never overwrite a date somebody has deliberately set;
--   * only ever move the date FORWARD. An earlier admission date would bill a
--     family sooner, which is the opposite of the bug and worse than it.
--
-- Where there is no trace at all, the row keeps its NULL and still bills from the
-- year. Nothing in the database says when that student joined, and inventing a
-- date would be a guess presented as a fact. Those are the rows the new admission
-- date field on Register Student exists for -- a human can say.

DO $$
DECLARE
    changed integer;
BEGIN
    WITH first_trace AS (
        SELECT a.entity_id AS student_id,
               CAST(MIN(a.created_at) AS date) AS first_seen
          FROM audit_logs a
         WHERE a.entity_type = 'Student'
           AND a.entity_id IS NOT NULL
         GROUP BY a.entity_id
    )
    UPDATE students s
       SET admission_date = ft.first_seen
      FROM first_trace ft, academic_years ay
     WHERE ft.student_id = s.id
       AND ay.id = s.academic_year_id
       AND (s.admission_date IS NULL OR s.admission_date = ay.start_date)
       AND ft.first_seen > ay.start_date
       AND (s.admission_date IS NULL OR ft.first_seen > s.admission_date);

    GET DIAGNOSTICS changed = ROW_COUNT;
    RAISE NOTICE 'V21: set admission_date from the first audit trace for % student(s)', changed;
END $$;

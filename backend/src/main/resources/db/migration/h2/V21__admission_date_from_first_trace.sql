-- H2 twin of the Postgres V21. Same intent; see that file for the full
-- reasoning, which is where it belongs rather than duplicated here.
--
-- Short version: a student whose admission_date is NULL bills from the academic
-- year's start, because that is billingStartFor's fallback -- so a mid-year
-- joiner reads as overdue from June and "Fix due dates" correctly reports
-- nothing to change. V18 only backfilled from 'STUDENT_ADDED' audit rows and
-- left NULL where there were none; every other student-scoped audit action
-- (STUDENT_UPDATED, ROSTER_BULK_IMPORT, XP_AWARDED, FEE_SCHEDULE_GENERATED, ...)
-- is also evidence, and the earliest of any of them beats the year start.
--
-- Two differences from the Postgres file, both forced by H2:
--
--   * No DO block, so no RAISE NOTICE and no row count in the log. H2 only ever
--     runs tests and local development, where the count is not the thing anyone
--     needs; production is Postgres and reports it.
--   * No UPDATE ... FROM, so the derived date is a correlated subquery. It reads
--     worse and is evaluated twice, which costs nothing at this size.

UPDATE students s
   SET admission_date = (
        SELECT CAST(MIN(a.created_at) AS date)
          FROM audit_logs a
         WHERE a.entity_type = 'Student'
           AND a.entity_id = s.id
   )
 WHERE EXISTS (
        SELECT 1
          FROM audit_logs a
         WHERE a.entity_type = 'Student'
           AND a.entity_id = s.id
   )
   -- Only a NULL, or exactly the year start: never overwrite a date somebody set.
   AND (
        s.admission_date IS NULL
        OR s.admission_date = (
             SELECT ay.start_date FROM academic_years ay WHERE ay.id = s.academic_year_id
           )
   )
   -- And only ever forward. An earlier admission date bills a family sooner,
   -- which is worse than the bug being fixed.
   AND (
        SELECT CAST(MIN(a.created_at) AS date)
          FROM audit_logs a
         WHERE a.entity_type = 'Student'
           AND a.entity_id = s.id
   ) > (
        SELECT ay.start_date FROM academic_years ay WHERE ay.id = s.academic_year_id
   )
   AND (
        s.admission_date IS NULL
        OR (
             SELECT CAST(MIN(a.created_at) AS date)
               FROM audit_logs a
              WHERE a.entity_type = 'Student'
                AND a.entity_id = s.id
           ) > s.admission_date
   );

# Dry run: giving old whole-grade tasks a section

**Nothing here writes anything.** Every query is a `SELECT`. Run them against
production Postgres, paste the results back, and the migration gets written to
match whatever is approved — not before.

## What the gap is

Before V20, a class task was keyed by grade alone. `teacher_tasks.class_section_id`
was added then, and every task set before it is `NULL`. Those tasks still reach
**every section of the grade**, deliberately:

```java
// TeacherTaskRepository.findClassTasksForSection
"and (t.classSectionId = :sectionId or t.classSectionId is null)"
```

So a task set for "Grade 6" in the old world appears on every 6-A, 6-B and 6-C
child's list. That was the reported bug, fixed for new tasks; these rows are the
residue. The web console now labels them honestly ("Grade 6 · all sections"), so
nothing is lying — the question is only whether any of them can be narrowed.

## The thing that shapes this whole job

**`teacher_tasks.created_by_teacher_id` is not a `users.id`.** It is
`UUID.nameUUIDFromBytes(email)` — an MD5-based UUID v3 of the teacher's email
address, with no foreign key:

```java
public UUID resolveTeacherId(String username) {
    if (username == null) return UUID.fromString("11111111-1111-1111-1111-111111111111");
    return UUID.nameUUIDFromBytes(username.getBytes());
}
```

Two consequences:

1. **Tasks cannot be joined to teachers in SQL.** So "the creating teacher only
   teaches one section of that grade, so they must have meant that one" — the
   obvious way to disambiguate — is not a query. It needs the hash recomputed per
   user, in a dialect where getting the UUID variant bits wrong fails silently.
2. This is the same invented-id pattern that `DashboardService.taughtSections`
   carries a scar from: it once matched that id against `ClassSection.teacherId`,
   never matched, fell through to an "all sections" fallback, **and that is how
   every teacher came to read the whole school's roster.** The id is still
   load-bearing for task ownership in `manageableTask`, which also means a teacher
   who changes their email address silently loses every task they have set.

That last point is worth its own piece of work and is not part of this backfill.

## Query 1 — is this worth doing at all?

```sql
SELECT count(*)                                              AS grade_wide_class_tasks,
       count(*) FILTER (WHERE task_status = 'ACTIVE')         AS still_active,
       count(*) FILTER (WHERE tenant_id IS NULL)              AS without_a_tenant,
       min(created_at)                                        AS oldest,
       max(created_at)                                        AS newest
FROM   teacher_tasks
WHERE  assigned_to_class = true
  AND  class_section_id IS NULL;
```

If `grade_wide_class_tasks` is zero, stop — there is nothing to backfill, and
that is the likeliest outcome for a pilot school that started after V20.

`without_a_tenant` matters because `teacher_tasks.tenant_id` is nullable (it is why
`TeacherTask` is exempt from `BaseTenantEntity` in the architecture rules). A task
with no tenant cannot be placed at all, and is reported separately below.

## Query 2 — Tier A: grades with only one section

**The only tier I would apply without your eye on each row.** If a grade has
exactly one section, "every section of Grade 6" and "6-A" name the same children.
Setting the column changes who sees the task not at all; it only makes the row
say what was always true.

```sql
WITH graded AS (
    SELECT cs.id, cs.tenant_id, cs.academic_year_id, cs.grade_name, cs.section_name,
           NULLIF(regexp_replace(cs.grade_name, '[^0-9]', '', 'g'), '')::int AS grade_number
    FROM   class_sections cs
), one_section AS (
    SELECT tenant_id, academic_year_id, grade_number,
           min(id)                                        AS only_section_id,
           min(grade_name || ' - ' || section_name)        AS only_section_label
    FROM   graded
    WHERE  grade_number IS NOT NULL
    GROUP  BY tenant_id, academic_year_id, grade_number
    HAVING count(*) = 1
)
SELECT t.id            AS task_id,
       tn.name         AS school,
       t.title,
       t.standard,
       t.task_status,
       t.created_at::date               AS set_on,
       o.only_section_label             AS would_be_set_to
FROM   teacher_tasks t
JOIN   one_section o ON o.tenant_id = t.tenant_id
                    AND o.grade_number = t.standard
LEFT   JOIN tenants tn ON tn.id = t.tenant_id
WHERE  t.assigned_to_class = true
  AND  t.class_section_id IS NULL
ORDER  BY tn.name, t.standard, t.created_at;
```

`grade_name` is free text and need not contain digits — "Nursery", "LKG" — which is
why the grade number is extracted with `NULLIF(...)` rather than a bare cast, and
why sections without one are excluded rather than crashing the query.
`academic_year_id` is deliberately not joined on: a task's is nullable, and a grade
having one section is a fact about the grade either way.

## Query 3 — Tier B: what has already been handed in

This is the honest substitute for "ask the teacher". If work has been handed in for
a sectionless task, the pupils who did it say which class it was really for.

```sql
WITH sectionless AS (
    SELECT id, tenant_id, title, standard
    FROM   teacher_tasks
    WHERE  assigned_to_class = true AND class_section_id IS NULL
), per_section AS (
    SELECT s.id                     AS task_id,
           s.title,
           s.standard,
           st.class_section_id,
           count(*)                 AS handed_in
    FROM   sectionless s
    JOIN   academic_submissions asub ON asub.teacher_task_id = s.id
    JOIN   students st               ON st.id = asub.student_id
    GROUP  BY s.id, s.title, s.standard, st.class_section_id
)
SELECT p.task_id,
       p.title,
       p.standard,
       count(*)                                     AS sections_that_handed_in,
       sum(p.handed_in)                             AS submissions,
       string_agg(cs.grade_name || ' - ' || cs.section_name || ' (' || p.handed_in || ')',
                  ', ' ORDER BY cs.section_name)    AS breakdown
FROM   per_section p
LEFT   JOIN class_sections cs ON cs.id = p.class_section_id
GROUP  BY p.task_id, p.title, p.standard
ORDER  BY count(*) DESC, p.title;
```

Read it like this:

- **`sections_that_handed_in = 1`** — one class did this work. Strong evidence, and
  narrowing to that section takes the task off nobody's list who engaged with it.
  Still yours to approve row by row.
- **`sections_that_handed_in > 1`** — **do not touch.** Children in more than one
  class did this work. Narrowing it would take the task off some of their lists
  while their submission stays on record, which is worse than a task that reaches
  too many people.

## Query 4 — Tier C: what is left, and stays left

Everything not in Tier A or B: a grade with several sections, no submissions to
learn from, and no way to reach the teacher who set it.

```sql
WITH graded AS (
    SELECT cs.tenant_id, cs.grade_name,
           NULLIF(regexp_replace(cs.grade_name, '[^0-9]', '', 'g'), '')::int AS grade_number
    FROM   class_sections cs
), multi AS (
    SELECT tenant_id, grade_number
    FROM   graded
    WHERE  grade_number IS NOT NULL
    GROUP  BY tenant_id, grade_number
    HAVING count(*) > 1
)
SELECT t.id AS task_id, tn.name AS school, t.title, t.standard, t.task_status,
       t.created_at::date AS set_on,
       (SELECT count(*) FROM academic_submissions a WHERE a.teacher_task_id = t.id) AS handed_in
FROM   teacher_tasks t
JOIN   multi m ON m.tenant_id = t.tenant_id AND m.grade_number = t.standard
LEFT   JOIN tenants tn ON tn.id = t.tenant_id
WHERE  t.assigned_to_class = true
  AND  t.class_section_id IS NULL
ORDER  BY handed_in DESC, t.created_at;
```

A task in Tier C with `handed_in = 0` and `task_status` not ACTIVE is arguably
better **closed** than narrowed: nobody did it, and guessing a section for it
invents information. That is a judgement for you, not a migration.

## What I would recommend

1. Run Query 1. If it is zero, we are done and no migration is needed.
2. Apply **Tier A** in a migration — it cannot change who sees anything.
3. Bring me Tier B's list and mark the rows you want narrowed. The migration can
   take an explicit list of task ids rather than a rule, which is safer for a
   handful of rows than any clever predicate.
4. Leave Tier C alone. Close the dead ones by hand if you want them off the lists.

## Not run from here

Written against the schema, not executed: this workspace has no production
connection, and getting one means handling the database credential. Column names
were taken from `V1__baseline.sql`, `V20__teacher_task_section.sql` and the
entities — `teacher_tasks` (`standard`, `assigned_to_class`, `class_section_id`,
`tenant_id`, `created_by_teacher_id`, `task_status`, `created_at`),
`class_sections` (`grade_name`, `section_name`), `academic_submissions`
(`student_id`, `teacher_task_id`), `students.class_section_id`.

The `regexp_replace` and `string_agg` calls are Postgres-specific and deliberately
so — production is Postgres, and these are reports rather than migrations. A
migration written from them will need an H2 twin, as every migration in this repo
does.

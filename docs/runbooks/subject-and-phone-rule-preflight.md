# Pre-flight for the subject and emergency-phone rules

Two server-side rules were added:

1. A task must be filed under a subject the caller is assigned to teach that
   class (`TasksService.validateTaskSubject`).
2. An emergency contact number must be a number a school can ring, the same rule
   the guardian number has (`PhoneNumbers`, via `StudentAdminService`).

Both fire **on write only**. Neither sweeps existing rows, and neither can reject
a record nobody is editing. These queries say what the live data looks like before
and after, so a cleanup can be planned rather than discovered.

Run them against production Postgres (Neon). They are read-only: `SELECT` only,
no `UPDATE`, no DDL. A read-only role is enough.

---

## 1. Teachers who cannot create tasks

A teacher with no subject assignment cannot set work. **This is not new** —
`teacherOwnsSection` has refused them whole-class tasks since R2-P1-2, with
"You are not assigned to \<class\>, so you cannot set work for it." The subject
rule adds one narrower case: a task aimed at a single named pupil, which had no
check at all before and is reachable through the API (not through the app, whose
student search only returns children in the teacher's own sections).

So this list is worth fixing for its own sake rather than because the new rule
breaks it. `TaskSubjectScopeTest.aTeacherWithNoAssignmentsWasAlreadyRefusedBySection`
pins that distinction.

```sql
SELECT t.name            AS school,
       u.full_name,
       u.email,
       u.role,
       u.approval_status,
       u.is_active
FROM   users u
JOIN   tenants t ON t.id = u.tenant_id
WHERE  u.role = 'TEACHER'
  AND  NOT EXISTS (
         SELECT 1 FROM subject_assignments sa WHERE sa.teacher_id = u.id
       )
ORDER  BY t.name, u.full_name;
```

`approval_status` and `is_active` are selected rather than filtered on purpose: a
teacher who has not been approved yet, or who has left, is expected to have no
assignments and needs no fixing. Only active, approved teachers on this list are a
problem.

Fix by assigning them a subject and section (Admin → Teacher Assignments), not by
relaxing the rule.

---

## 2. Emergency contact numbers that break the rule

The rule is `PhoneNumbers.isValid`: an optional leading `+`, then digits, spaces,
hyphens and brackets, with **at least seven digits**. Blank stays allowed — the
field is optional.

Existing rows are left alone. A record keeps an unusable number until somebody
edits that field, and then it has to be right; an unrelated edit to the same
child's record still saves (`EmergencyContactPhoneTest
.anUnrelatedEditToARecordWithABadNumberStillSaves`).

### How many

```sql
SELECT count(*) AS breaking_the_rule
FROM   students s
WHERE  s.emergency_contact_phone IS NOT NULL
  AND  btrim(s.emergency_contact_phone) <> ''
  AND  (
         btrim(s.emergency_contact_phone) !~ '^\+?[0-9[:space:]()-]{7,}$'
         OR length(regexp_replace(s.emergency_contact_phone, '[^0-9]', '', 'g')) < 7
       );
```

### Which ones, to fix them

```sql
SELECT t.name            AS school,
       cs.grade_name,
       cs.section_name,
       s.first_name,
       s.last_name,
       s.emergency_contact_phone
FROM   students s
JOIN   tenants t       ON t.id  = s.tenant_id
LEFT   JOIN class_sections cs ON cs.id = s.class_section_id
WHERE  s.emergency_contact_phone IS NOT NULL
  AND  btrim(s.emergency_contact_phone) <> ''
  AND  (
         btrim(s.emergency_contact_phone) !~ '^\+?[0-9[:space:]()-]{7,}$'
         OR length(regexp_replace(s.emergency_contact_phone, '[^0-9]', '', 'g')) < 7
       )
ORDER  BY t.name, cs.grade_name, cs.section_name, s.last_name;
```

Both conditions are needed, and the second is not redundant: the character class
alone accepts `((( )))-` — seven punctuation marks and no number at all. That is
the same pair of checks `PhoneNumbers.isValid` makes, in the same order.

### For context, how many have one at all

```sql
SELECT count(*) FILTER (WHERE emergency_contact_phone IS NOT NULL
                          AND btrim(emergency_contact_phone) <> '') AS with_a_number,
       count(*)                                                     AS students
FROM   students;
```

A large `students` count with a small `with_a_number` count means the rule has
little to bite on, and the gap is the more interesting problem.

---

## Not run from here

These were written against the schema, not executed: this workspace has no
connection to production, and fetching one would mean handling the database
credential. Column names were taken from `V1__baseline.sql` and the entities
(`users`, `subject_assignments`, `students.emergency_contact_phone`,
`class_sections`), and the regex mirrors `PhoneNumbers.PATTERN` character for
character.

One caution on that regex, learned the hard way on the same rule's HTML
counterpart: **a regex that is correct in one dialect can be invalid in another,
and an engine that rejects it may say nothing useful.** If either query errors,
the likely cause is the bracket expression, not the logic.

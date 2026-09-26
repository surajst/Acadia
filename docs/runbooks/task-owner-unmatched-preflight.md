# Preflight: which tasks V24 will leave ownerless

V24 fills `teacher_tasks.created_by_user_id` from the hashed owner column beside
it. Rows it cannot match keep a null and are logged row by row; nothing is
guessed. **These queries say which rows those will be, before the migration runs.**

Read-only. `SELECT` only, no writes.

## First, prove the hash expression is right

`created_by_teacher_id` holds `UUID.nameUUIDFromBytes(email)` — an MD5-based
version 3 UUID. Reproducing one in SQL means hashing the address and then patching
the version and variant nibbles by hand, and getting that wrong produces a
valid-looking UUID that quietly matches nothing.

So check it before trusting anything below. This must return `true`:

```sql
WITH v3 AS (
    SELECT 'teacher@greenwood.com'::text AS email
), h AS (
    SELECT email, md5(email) AS m FROM v3
)
SELECT email,
       (substr(m,1,8) || '-' || substr(m,9,4) || '-3' || substr(m,14,3) || '-' ||
        to_hex((('x' || substr(m,17,1))::bit(4)::int & 3) | 8) || substr(m,18,3) || '-' ||
        substr(m,21,12))::uuid                                   AS computed,
       (substr(m,1,8) || '-' || substr(m,9,4) || '-3' || substr(m,14,3) || '-' ||
        to_hex((('x' || substr(m,17,1))::bit(4)::int & 3) | 8) || substr(m,18,3) || '-' ||
        substr(m,21,12))::uuid
         = '01b728e6-4b09-3f2e-8d9c-1b23175c33f3'::uuid          AS matches_java
FROM h;
```

`01b728e6-4b09-3f2e-8d9c-1b23175c33f3` is what `UUID.nameUUIDFromBytes` returns for
that address — taken from running it, not from memory. The formula was checked
against four addresses including a mixed-case one, and reproduces Java exactly.

**If `matches_java` is false, stop and tell me.** Everything below depends on it,
and a wrong hash would report every row as unmatched.

## The list

Rows here are the ones a teacher has already lost — they cannot edit, close or
delete this work today either, because the address their ownership was hashed
from no longer exists. V15 created most of them by lowercasing stored addresses.

```sql
WITH owner_hash AS (
    SELECT u.id AS user_id,
           u.email,
           (substr(md5(u.email),1,8) || '-' || substr(md5(u.email),9,4) || '-3' ||
            substr(md5(u.email),14,3) || '-' ||
            to_hex((('x' || substr(md5(u.email),17,1))::bit(4)::int & 3) | 8) ||
            substr(md5(u.email),18,3) || '-' || substr(md5(u.email),21,12))::uuid AS hashed
    FROM   users u
    WHERE  u.email IS NOT NULL AND u.email <> ''
)
SELECT t.id                        AS task_id,
       tn.name                     AS school,
       t.title,
       t.standard,
       t.task_status,
       t.created_at::date          AS set_on,
       t.created_by_teacher_id     AS owner_hash,
       (SELECT count(*) FROM academic_submissions a WHERE a.teacher_task_id = t.id) AS handed_in
FROM   teacher_tasks t
LEFT   JOIN tenants tn ON tn.id = t.tenant_id
WHERE  NOT EXISTS (SELECT 1 FROM owner_hash o WHERE o.hashed  = t.created_by_teacher_id)
  AND  NOT EXISTS (SELECT 1 FROM owner_hash o WHERE o.user_id = t.created_by_teacher_id)
ORDER  BY tn.name, t.created_at;
```

The two `NOT EXISTS` clauses are V24's two matching rules, negated:

1. the column holds the hash of a live address — what `TeacherTaskService` writes;
2. the column holds a real `users.id` — what `ScreenContentSeeder` writes, and the
   reason seeded demo tasks never appeared in a teacher's own task list.

## The summary, if the list is long

```sql
WITH owner_hash AS (
    SELECT u.id AS user_id,
           (substr(md5(u.email),1,8) || '-' || substr(md5(u.email),9,4) || '-3' ||
            substr(md5(u.email),14,3) || '-' ||
            to_hex((('x' || substr(md5(u.email),17,1))::bit(4)::int & 3) | 8) ||
            substr(md5(u.email),18,3) || '-' || substr(md5(u.email),21,12))::uuid AS hashed
    FROM   users u
    WHERE  u.email IS NOT NULL AND u.email <> ''
)
SELECT count(*)                                                        AS tasks_total,
       count(*) FILTER (WHERE EXISTS (SELECT 1 FROM owner_hash o
                                       WHERE o.hashed = t.created_by_teacher_id))  AS by_email_hash,
       count(*) FILTER (WHERE EXISTS (SELECT 1 FROM owner_hash o
                                       WHERE o.user_id = t.created_by_teacher_id)) AS already_a_user_id,
       count(*) FILTER (WHERE NOT EXISTS (SELECT 1 FROM owner_hash o
                                           WHERE o.hashed  = t.created_by_teacher_id)
                          AND NOT EXISTS (SELECT 1 FROM owner_hash o
                                           WHERE o.user_id = t.created_by_teacher_id)) AS will_be_ownerless
FROM   teacher_tasks t;
```

These three should add up to `tasks_total`. If they do not, a row matched both
rules, which cannot happen unless a user's id collides with some user's email hash
— worth knowing about if it ever shows up.

## What to do with the list

Nothing automatic. An ownerless task is still visible to every pupil it was set
for and still manageable by an **admin or principal** — only the teacher who set it
is locked out, and they already were. Options, in order of how much they claim to
know:

1. **Leave them.** Honest, and costs a teacher the ability to close old work.
2. **Close the dead ones** from the web console, if nobody handed anything in
   (`handed_in = 0`) and they are long past.
3. **Reassign by hand**, if you know who set a particular one. That is a fact from
   outside the database, which is exactly why the migration will not invent it.

## Not run from here

Written against the schema and verified against Java's output, but not executed:
this workspace has no production connection, and getting one means handling the
database credential. The hash expression was checked in a second implementation
against four addresses, including `Priya@Example.com` and `priya@example.com`,
which hash to entirely different UUIDs — the whole reason these rows exist.

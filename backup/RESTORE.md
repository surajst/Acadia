# Restoring the database from a backup

A backup nobody has restored is a guess. Walk this through once against a Neon
branch before you need it, and again whenever the schema changes shape — the
step that fails is never the one you expect.

Backups land in `s3://acadia-db-backups-947752017191/` as
`daily/acadia-<TS>.dump` (kept 35 days) and `weekly/acadia-<TS>.dump` (kept a
year, on Sundays). `<TS>` is UTC, `2026-09-25T2030Z`.

**Restore into a branch, never into production.** A `pg_restore` aimed at the
live database is how a bad night becomes an unrecoverable one.

---

## 1. Create a Neon branch to restore into

Neon console → the project → **Branches** → **New branch**, from `main`. Copy
its connection string; it looks like the production one with a different host.

A branch is the right target because it is disposable, it costs nothing to throw
away, and comparing it against production afterwards is the only way to know the
restore worked.

## 2. Download the dump from S3

**In the AWS console**, not with the backup job's credentials. The
`acadia-backup-writer` IAM user can only `PutObject` — it cannot list or read
the bucket, deliberately, so that a leaked backup key cannot exfiltrate every
backup. Use your own admin login.

S3 → `acadia-db-backups-947752017191` → `daily/` (or `weekly/`) → pick the
object → **Download**. Versioning is on, so an overwritten object still has its
older versions under **Versions**.

## 3. Restore

```sh
pg_restore --no-owner --no-privileges -d "<branch connection string>" acadia-<TS>.dump
```

`--no-owner` and `--no-privileges` because the dump was taken that way: the role
names in production do not exist on the branch, and without these flags every
`ALTER ... OWNER TO` fails and the output is a wall of errors around an
otherwise fine restore.

Your local `pg_restore` must be **18 or newer** — same rule as the dump side.

Expect some noise on stderr about roles or extensions that already exist. What
matters is the exit code and step 4, not a clean log.

## 4. Check it actually restored, by counting rows

This is the step that makes the whole thing worth doing. Run against **both**
the branch and production, and compare:

```sql
SELECT 'users'        AS t, count(*) FROM users
UNION ALL SELECT 'students',     count(*) FROM students
UNION ALL SELECT 'fee_invoices', count(*) FROM fee_invoices
UNION ALL SELECT 'attendance',   count(*) FROM attendance
UNION ALL SELECT 'audit_logs',   count(*) FROM audit_logs
ORDER BY t;
```

The branch's counts should match production's as of the backup's timestamp —
lower where rows were added since, never higher, and never zero.

> Two of these names differ from the task sheet, checked against
> `V1__baseline.sql`: the tables are **`attendance`** (not `attendance_records`)
> and **`audit_logs`** (not `audit_log`). A restore check that fails with
> "relation does not exist" at 3am reads like a broken backup rather than a
> typo in the runbook, so they are corrected here rather than copied.

A count of zero on a table that has rows in production means the restore
silently did nothing for it — check the `pg_restore` output for that table
rather than assuming the dump was bad.

## 5. Point the application at the branch, if this is a real recovery

Render → `acadia-backend-sg` → **Environment** → change `DB_HOST`, `DB_NAME`,
`DB_USERNAME`, `DB_PASSWORD` to the branch's, then redeploy.

Two things about this repo specifically:

- `render.yaml` marks every `DB_*` var `sync: false`, so the blueprint will not
  overwrite what you set by hand. Keep it that way.
- The schema is Flyway-owned with `ddl-auto=validate`. A restored dump already
  carries `flyway_schema_history`, so the app starts and finds nothing to do. A
  restore that skipped that table makes Flyway try to baseline a populated
  schema and the app refuses to start — which is a safe failure, not data loss.

---

## What this does not cover

- **Point-in-time recovery.** These are nightly snapshots; anything written
  after the last one is gone. Neon's own PITR is a separate, better tool for a
  recent mistake — use it first if the damage is hours old.
- **Uploaded files.** Profile photos live in the database (`users.photo`), so
  they come back with it. Anything ever moved to object storage would not.
- **A tested restore.** Nobody has run this end to end yet. Until somebody has,
  treat it as a plan rather than a procedure.

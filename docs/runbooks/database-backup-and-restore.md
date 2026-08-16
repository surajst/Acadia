# Runbook: database backup and restore

Covers the production Postgres behind `acadia-backend-sg`. Read the whole thing
before a restore — the order of operations matters, and one step (Flyway
baselining) is easy to get wrong in a way that corrupts a recovered database.

**Status: rehearsed end to end on 2026-08-16.** A production dump was restored
into a scratch database, the application was booted against it, and a real user
logged in and read their school's roster. Measured numbers and what the drill
taught are in [Drill results](#drill-results).

**Current coverage in one line: a 7-day history window on Neon's Launch plan, and
nothing else.** No dumps are scheduled. See
[The history window](#the-history-window).

---

## What is being protected

One Postgres database, 35 tables, holding every school's records: students,
guardians, attendance, assessments, fees, messages. There is no second copy of
this data anywhere. The application can be rebuilt from git in minutes; the
database cannot be rebuilt at all.

Schema is owned by **Flyway** (`db/migration/postgresql`, V1 baseline), and
Hibernate runs with `ddl-auto=validate`. This matters for restores: the
application will refuse to start if the restored schema does not match the
entities, which is a feature — it fails loudly instead of silently corrupting.

---

## Backup

### Which database is production? Read this first

**Production runs on Neon**, not on a Render database:
`ep-icy-star-azweqd7y.c-3.ap-southeast-1.aws.neon.tech` (ap-southeast-1). The
original Render Postgres was on the free plan, expired, was deactivated, and
crash-looped the backend with `UnknownHostException` on `DB_HOST`; the data moved
to Neon after that.

A leftover Render `acadia-postgres` instance may still exist in the workspace, and
its dashboard page shows perfectly healthy connection details. **It is not
production.** The only authoritative answer is:

    Render -> acadia-backend-sg -> Environment -> DB_HOST

Never the database's own page — that page tells you a database exists, not that
anything connects to it. Backing up the wrong database is worse than having no
backup, because it looks like coverage.

### Managed backups (primary)

Neon restores from **history**, not from dump files: you create a branch at a past
timestamp and promote it after checking it. That is a good default because it is
non-destructive — the current state survives while you inspect the candidate.

### The history window

**7 days**, set 2026-08-13 in the Neon console (Settings -> Storage -> History
window). That is the maximum on the Launch plan; the slider stops at 7d.

It was 6 hours on the Free plan, which was close to useless in practice — a bad
import on a Friday afternoon was unrecoverable by Monday morning, and anything
happening overnight was gone before anyone looked. Seven days matches how a
school office actually works: problems get noticed the next time somebody opens
the data, which is the next working day or the one after.

**Read the window as a detection deadline, not as a backup.** Recovery still
requires someone to notice within seven days. Nothing here helps with a mistake
discovered at the end of term.

**It covers exactly one class of failure: recent bad writes.** It does nothing
about account loss, a billing lapse, or the provider going away — which is the
failure that already destroyed the previous Render database on this project. The
history lives inside the same Neon account as the data it protects.

**So one gap remains: independent dumps.** One recent dump held outside the
provider is what survives losing the account. Not urgent while the database holds
~30 MB and no real records; do it before the first school onboards, not after.

History storage bills at $0.20/GB-month. At the current size, 7 days of history
costs cents — this is not a setting to economise on.

### Manual dump (before anything risky)

Take one of these before any migration, bulk import, or plan change:

```bash
pg_dump "$DATABASE_URL" --format=custom --file=acadia-$(date +%Y%m%d-%H%M).dump
```

`--format=custom` (not plain SQL) is what makes selective restore and parallel
restore possible later.

**Handle the dump file as student personal data.** It contains children's names,
guardians, and contact details. Do not put it in the repository (`.gitignore`
already excludes `prod-schema.sql` after one such near-miss), do not attach it to
an issue, and delete it from local disk when finished.

A schema-only dump is safe to share and useful for debugging migrations:

```bash
pg_dump "$DATABASE_URL" --schema-only --file=schema.sql
```

---

## Restore

### Decide what kind of failure this is

| Situation | Action |
|---|---|
| Bad data written by the app (bad import, wrong bulk update) | Point-in-time restore to just before the write |
| Database deleted / expired / provider-side loss | Restore latest backup into a new instance |
| Schema drift — app won't start, Hibernate validation fails | Do **not** restore; fix the migration. See [Flyway](#flyway-after-a-restore) |
| Single tenant's data damaged | Restore to a **scratch** database, extract that tenant's rows, apply to prod |

Never restore a whole database to fix one tenant. This is multi-tenant: a full
restore rolls back every other school too.

### Full restore

1. **Stop writes.** Render dashboard -> `acadia-backend-sg` -> suspend the service.
   A running app will keep writing into a database you are mid-restore on.
2. **Restore into a NEW database or branch**, never over the live one. If the
   restore is bad you still have the original to try again from. On Neon this is
   the default shape: restore creates a branch you promote afterwards.
3. Point `DB_HOST` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` at the restored
   target. These are `sync: false` in `render.yaml` precisely so the blueprint
   cannot overwrite them — set them in the dashboard only, and do **not** add a
   `databases:` block to the blueprint to "manage" them.
4. **Verify before resuming traffic** — see [Post-restore verification](#post-restore-verification).
5. Resume the service.

```bash
pg_restore --dbname="$NEW_DATABASE_URL" --no-owner --no-privileges acadia-....dump
```

`--no-owner --no-privileges` avoids failures when the restored role names do not
exist on the new instance, which is the common case across providers.

### Flyway after a restore

This is the step that silently corrupts things if rushed.

A restored dump **already contains** the `flyway_schema_history` table and every
applied migration. Do not run a baseline against it and do not delete that table.
`spring.flyway.baseline-on-migrate=true` only applies to a database with no
history table at all; with history present, Flyway correctly picks up where it
left off.

If the dump was schema-only or partial and history is missing, Flyway will
baseline at V1 and consider later migrations unapplied — it will then try to
re-run them against a schema that already has those objects, and fail. Restore a
full dump instead of hand-repairing this.

---

## Post-restore verification

Do all of these before letting users back in.

1. **Row counts against expectation**, per tenant — a restore that silently drops
   a tenant looks fine at the application level until that school logs in:

   ```sql
   SELECT tenant_id, count(*) FROM student GROUP BY tenant_id ORDER BY 2 DESC;
   SELECT count(*) FROM tenant;
   ```

2. **Application starts clean.** `ddl-auto=validate` means a successful boot is
   real evidence the schema matches the entities. A startup failure here means
   the restore is incomplete — do not "fix" it by relaxing validate.

3. **Health is honest:**

   ```bash
   curl -s https://portal.concept-edu.com/actuator/health
   ```

   Expect `{"status":"UP"}`. The `db` health indicator is enabled, so `UP` means
   the connection genuinely works — this endpoint was specifically changed from
   `/login` because `/login` returns 200 even with a dead database.

4. **Log in as a real user of a restored tenant** and load a page that reads
   several tables (admin dashboard). Row counts prove data exists; only a real
   login proves it is coherent.

---

## Drill results

Rehearsed 2026-08-16 against a real production dump.

| Step | Measured |
|---|---|
| `pg_dump` of production (30 MB database, 88 KB custom-format dump) | seconds |
| `pg_restore` into a scratch local database | **under 1 second** |
| Application boot against the restored database | **7.3 seconds** |
| Verified: health UP, real login, tenant roster returned the right student | — |

**Recovery time is dominated by human decisions, not by the machinery.** Every
mechanical step finished in seconds; what took real time was working out which
credentials to use. Plan the RTO around people, not `pg_restore`.

### What the drill confirmed

- **Flyway leaves a restored dump alone.** This was the open question. The log
  read `Current version of schema "public": 1` then `Schema "public" is up to
  date. No migration necessary.` It did not re-apply the baseline against a
  schema that already had every table.
- **`ddl-auto=validate` passing on boot is real evidence.** 36 tables restored
  and the application started clean, which means the restored schema genuinely
  matches the entities.
- **`--no-owner --no-privileges` was required**, as written. The dump's roles do
  not exist on a different server.

### What the drill changed

- **Use Neon's NON-pooled host for `pg_dump`.** The pooled endpoint
  (`...-pooler....neon.tech`) runs through PgBouncer, which does not support
  what `pg_dump` needs. Drop `-pooler` from the hostname.
- **Restoring the data is not the same as regaining access.** Nobody knew the
  passwords for the accounts in the dump, so the restore was provably good while
  still being unusable until a password was reset directly in the restored
  database. In a real incident the school's own admin password comes from their
  password manager -- but if it does not, resetting `users.password_hash` to a
  known bcrypt value is the way back in, and that is a step worth knowing before
  you need it.
- **Keep credentials in `pgpass.conf`, not on the command line.** Both the Neon
  and local entries live there, so no dump or restore command contains a
  password to leak into shell history.

### Still not covered

The drill restored a dump taken minutes earlier. It did not exercise Neon's
branch-based point-in-time restore, which is what an actual "bad write two days
ago" incident would use. Worth a second drill before that matters.

## Restore rehearsal

Do this once, now, while nothing is on fire. It is the only thing that converts
this document from a plan into a backup.

1. Take a dump of production.
2. Restore it into a scratch database (a local Postgres container is fine).
3. Point a **local** backend at it with `SPRING_PROFILES_ACTIVE=postgres` and
   `DB_*` set to the scratch instance, `APP_DEV_MODE=false`.
4. Confirm it boots (validate passes), log in, load the dashboard.
5. Write down how long the whole thing took.

That last number is the real recovery time objective. Everything before it is
theory.

---

## Related

- Deployment topology and how to tell what is actually deployed: `docs/adr/`
  and the Render blueprint `render.yaml`.
- Schema ownership rationale: comments in
  `backend/src/main/resources/application.properties` and the V1 baseline header
  in `backend/src/main/resources/db/migration/postgresql/V1__baseline.sql`.

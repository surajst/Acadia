# Runbook: database backup and restore

Covers the production Postgres behind `acadia-backend`. Read the whole thing
before a restore — the order of operations matters, and one step (Flyway
baselining) is easy to get wrong in a way that corrupts a recovered database.

**Status: the restore procedure below has NOT been rehearsed.** It is written
from the schema and config as they stand, not from a drill. Do the drill in
[Restore rehearsal](#restore-rehearsal) before you need it for real. An
unrehearsed restore is a plan, not a backup.

**Current coverage in one line: a 6-hour history window on Neon's Free plan, and
nothing else.** No dumps are scheduled. See
[The history window is 6 hours](#the-history-window-is-6-hours).

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

    Render -> acadia-backend -> Environment -> DB_HOST

Never the database's own page — that page tells you a database exists, not that
anything connects to it. Backing up the wrong database is worse than having no
backup, because it looks like coverage.

### Managed backups (primary)

Neon restores from **history**, not from dump files: you create a branch at a past
timestamp and promote it after checking it. That is a good default because it is
non-destructive — the current state survives while you inspect the candidate.

### The history window is 6 hours

Measured in the Neon console (Settings -> Storage -> History window) on
2026-08-13. The slider is already at its **maximum for the Free plan**; there is
nothing to turn up. Paid plans go to 30 days.

**Read this as a detection deadline, not as a backup.** Restoring requires
someone to notice the problem within six hours of it happening. Concretely, on
the current plan:

| When the damage happens | Recoverable? |
|---|---|
| Bad import at 10am, spotted by lunch | Yes |
| Bad import Friday 4pm, spotted Monday | **No. Gone.** |
| Anything overnight | **No**, unless someone is watching at 2am |

A school office does bulk imports during the working day and nobody looks at the
data again until the next morning. The overlap between "when damage occurs" and
"within six hours of someone noticing" is small, which means the effective
coverage is much weaker than "we have point-in-time restore" suggests.

It also protects against exactly one class of failure: recent bad writes. It does
nothing for account loss, billing lapse, or the provider going away — the failure
that already destroyed the previous Render database on this project.

**Therefore, before a real school's data goes in, do both:**

1. **Upgrade the Neon plan.** Six hours to thirty days is the single highest-value
   change available, and it is a billing decision rather than an engineering one.
2. **Keep independent dumps** (below). Even a 30-day window lives inside the same
   account; one recent dump held elsewhere is what survives losing the account.

Neither is urgent while the database holds ~30 MB and no real records. Both become
urgent the day the first school onboards. Do them before that day, not after.

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

1. **Stop writes.** Render dashboard -> `acadia-backend` -> suspend the service.
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
   curl -s https://acadia-backend-rx3l.onrender.com/actuator/health
   ```

   Expect `{"status":"UP"}`. The `db` health indicator is enabled, so `UP` means
   the connection genuinely works — this endpoint was specifically changed from
   `/login` because `/login` returns 200 even with a dead database.

4. **Log in as a real user of a restored tenant** and load a page that reads
   several tables (admin dashboard). Row counts prove data exists; only a real
   login proves it is coherent.

---

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

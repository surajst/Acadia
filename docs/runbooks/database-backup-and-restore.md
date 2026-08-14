# Runbook: database backup and restore

Covers the production Postgres behind `acadia-backend`. Read the whole thing
before a restore — the order of operations matters, and one step (Flyway
baselining) is easy to get wrong in a way that corrupts a recovered database.

**Status: the restore procedure below has NOT been rehearsed.** It is written
from the schema and config as they stand, not from a drill. Do the drill in
[Restore rehearsal](#restore-rehearsal) before you need it for real. An
unrehearsed restore is a plan, not a backup.

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

### Managed backups (primary)

Render's **free** Postgres plan has **no automatic backups**, and free instances
expire and are deleted on a timer. A free instance already expired once on this
project and crash-looped the backend with `UnknownHostException` on `DB_HOST`.

Paid plans provide automatic daily backups and point-in-time recovery. Confirm
the current plan and its retention window in the Render dashboard under the
database's **Recovery** / **Backups** section — do not assume from this document,
plan features change.

To upgrade: Render dashboard -> `acadia-postgres` -> Settings -> change the
instance plan. This does not require a schema change or an application redeploy;
`DB_*` env vars keep resolving through `fromDatabase` in `render.yaml`.

Keep `render.yaml` in step with whatever plan is chosen, or a future blueprint
sync will silently move it back (this exact trap already caught the web service:
see the `plan: starter` comment there).

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
2. **Restore into a NEW database instance**, never over the live one. If the
   restore is bad you still have the original to try again from.
3. Point `DB_HOST` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` at the new
   instance. If it is a new Render database, update the `fromDatabase` name in
   `render.yaml` too, or the next blueprint sync will point back at the old one.
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

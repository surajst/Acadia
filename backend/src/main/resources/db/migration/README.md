# Database migrations (Flyway)

Flyway owns the schema. Hibernate runs with `ddl-auto=validate` — it **verifies**
the database matches the entities at startup and fails fast on drift, and never
mutates the schema itself. The legacy `schema.sql` + `ddl-auto=update` approach
has been retired.

## Layout

Migrations are **vendor-specific** because H2 (local/test) and PostgreSQL (prod)
have different DDL. Flyway resolves `{vendor}` at runtime:

```
db/migration/h2/          -> used on H2         (local dev, tests, CI)
db/migration/postgresql/  -> used on PostgreSQL (production)
```

`V1__baseline.sql` in each folder is the **entity-derived** baseline: generated
offline from the JPA `@Entity` model via Hibernate schema export (35 tables).

## How existing databases are adopted (no data touched)

`spring.flyway.baseline-on-migrate=true` with `baseline-version=1`:

- **Fresh/empty DB** (a new test/CI database): Flyway applies `V1` normally.
- **Existing pre-Flyway DB** (production, local dev file): Flyway finds a
  non-empty schema with no history table, **baselines it at V1 without
  re-running the DDL**, then Hibernate `validate` confirms it matches.

Both paths are verified on the `harden/flyway-migrations` branch.

## ⚠️ Reconciliation gate — before the FIRST production deploy

The baseline is derived from the *entities*, i.e. the *intended* schema. The
live production database was built over time by the old `ddl-auto=update`, so it
may have accreted drift (extra columns, differing types/constraints). If prod's
real schema differs from `postgresql/V1__baseline.sql`, `ddl-auto=validate` will
**fail startup on the next deploy**.

Before deploying this to prod:

1. `pg_dump --schema-only` the live database.
2. Diff it against `db/migration/postgresql/V1__baseline.sql`.
3. Patch the baseline to match reality (or fix the drift with a forward
   migration), so `validate` passes against the real prod schema.

## Adding a new change

Never edit `V1__baseline.sql` (or any applied migration). Add a new file to
**both** vendor folders:

```
db/migration/h2/V2__add_something.sql
db/migration/postgresql/V2__add_something.sql
```

Then update the entities to match. `validate` will confirm they agree.

-- Empties every school out of the database, keeping the schema and Flyway's
-- migration history intact.
--
-- WHEN THIS IS THE RIGHT TOOL: the database holds nothing but test data and you
-- want a clean start before onboarding a real school. It deletes EVERY tenant.
-- To remove one school and keep the others, use purge_tenant.sql instead.
--
-- Why TRUNCATE rather than per-table DELETEs: CASCADE resolves the foreign-key
-- order for us, so there is no dependency order to encode and nothing to rot as
-- the schema grows. flyway_schema_history is excluded deliberately -- drop that
-- and the next deploy tries to re-apply V1 against a schema that already has
-- every table, which fails and leaves the service down.
--
-- USAGE -- there is no undo, so take a dump first:
--
--   BEGIN;
--   \i wipe_all_school_data.sql
--   -- read the NOTICE output and the counts below, then:
--   COMMIT;    -- or ROLLBACK; if anything looks wrong

DO $$
DECLARE
    v_tables text;
    v_count  bigint;
BEGIN
    SELECT count(*) INTO v_count FROM tenants;
    RAISE NOTICE 'Tenants about to be removed: %', v_count;

    SELECT string_agg(format('%I.%I', schemaname, tablename), ', ')
      INTO v_tables
      FROM pg_tables
     WHERE schemaname = 'public'
       AND tablename <> 'flyway_schema_history';

    IF v_tables IS NULL THEN
        RAISE EXCEPTION 'No application tables found -- is this the right database?';
    END IF;

    EXECUTE format('TRUNCATE TABLE %s RESTART IDENTITY CASCADE', v_tables);
    RAISE NOTICE 'Truncated: %', v_tables;
END $$;

-- Proof, to read before committing. Every count must be 0, and the Flyway
-- history must still list its migrations -- that is what keeps the next deploy
-- from trying to rebuild the schema from scratch.
SELECT 'tenants' AS table_name, count(*) AS remaining FROM tenants
UNION ALL SELECT 'users', count(*) FROM users
UNION ALL SELECT 'students', count(*) FROM students
UNION ALL SELECT 'parents', count(*) FROM parents
UNION ALL SELECT 'fee_invoices', count(*) FROM fee_invoices
UNION ALL SELECT 'flyway (must NOT be 0)', count(*) FROM flyway_schema_history
ORDER BY 1;

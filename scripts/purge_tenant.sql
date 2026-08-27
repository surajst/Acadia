-- Deletes every row belonging to one tenant, by subdomain.
--
-- The dev-mode purge endpoint does not exist in production by design, so this
-- is the equivalent for a real database. It mirrors TenantPurgeService: the
-- table list is DISCOVERED from information_schema rather than hardcoded (26+
-- entities carry tenant_id and the schema keeps growing), join tables with no
-- tenant_id of their own are reached through their foreign keys, and deletes
-- are retried until a pass makes no progress, which converges on the correct
-- foreign-key order without encoding one that would rot.
--
-- USAGE -- read this before running:
--   1. Take a dump first. There is no undo.
--   2. Run inside an explicit transaction and CHECK THE COUNTS before COMMIT:
--
--        BEGIN;
--        \set target_subdomain 'the-subdomain'
--        \i purge_tenant.sql
--        -- inspect the NOTICE output, then:
--        COMMIT;   -- or ROLLBACK; if anything looks wrong
--
--   3. It refuses to run against a subdomain that does not exist, rather than
--      silently deleting nothing and reporting success.

DO $$
DECLARE
    v_subdomain  text := current_setting('purge.subdomain', true);
    v_tenant_id  uuid;
    v_sql        text;
    v_table      text;
    v_rows       bigint;
    v_total      bigint := 0;
    v_progress   boolean := true;
    v_pending    text[];
    v_failed     text[];
    v_stmts      jsonb := '{}'::jsonb;
BEGIN
    IF v_subdomain IS NULL OR v_subdomain = '' THEN
        RAISE EXCEPTION 'Set the target first:  SET LOCAL purge.subdomain = ''some-subdomain'';';
    END IF;

    SELECT id INTO v_tenant_id FROM tenants WHERE subdomain = v_subdomain;
    IF v_tenant_id IS NULL THEN
        RAISE EXCEPTION 'No tenant with subdomain %  (nothing deleted)', v_subdomain;
    END IF;
    RAISE NOTICE 'Purging tenant % (%)', v_subdomain, v_tenant_id;

    -- Every table carrying tenant_id, except the tenants table itself.
    FOR v_table IN
        SELECT c.table_name
          FROM information_schema.columns c
          JOIN information_schema.tables t
            ON t.table_name = c.table_name AND t.table_schema = c.table_schema
         WHERE c.table_schema = 'public'
           AND c.column_name = 'tenant_id'
           AND t.table_type = 'BASE TABLE'
           AND c.table_name <> 'tenants'
    LOOP
        v_stmts := v_stmts || jsonb_build_object(v_table,
            format('DELETE FROM %I WHERE tenant_id = %L', v_table, v_tenant_id));
    END LOOP;

    -- Join tables (e.g. student_parents) hold tenant data but carry no
    -- tenant_id; without these the rows they reference cannot be deleted and
    -- the purge stalls on students, parents and the class tables.
    FOR v_table, v_sql IN
        SELECT tc.table_name,
               format('DELETE FROM %I WHERE %I IN (SELECT %I FROM %I WHERE tenant_id = %L)',
                      tc.table_name, kcu.column_name, ccu.column_name, ccu.table_name, v_tenant_id)
          FROM information_schema.table_constraints tc
          JOIN information_schema.key_column_usage kcu
            ON kcu.constraint_name = tc.constraint_name AND kcu.table_schema = tc.table_schema
          JOIN information_schema.constraint_column_usage ccu
            ON ccu.constraint_name = tc.constraint_name AND ccu.table_schema = tc.table_schema
         WHERE tc.constraint_type = 'FOREIGN KEY'
           AND tc.table_schema = 'public'
           AND NOT v_stmts ? tc.table_name
           AND v_stmts ? ccu.table_name
    LOOP
        IF NOT v_stmts ? v_table THEN
            v_stmts := v_stmts || jsonb_build_object(v_table, v_sql);
        END IF;
    END LOOP;

    SELECT array_agg(k) INTO v_pending FROM jsonb_object_keys(v_stmts) k;

    WHILE array_length(v_pending, 1) > 0 AND v_progress LOOP
        v_progress := false;
        v_failed := ARRAY[]::text[];
        FOREACH v_table IN ARRAY v_pending LOOP
            BEGIN
                EXECUTE (v_stmts ->> v_table);
                GET DIAGNOSTICS v_rows = ROW_COUNT;
                v_total := v_total + v_rows;
                IF v_rows > 0 THEN
                    RAISE NOTICE '  % : % rows', v_table, v_rows;
                END IF;
                v_progress := true;
            EXCEPTION WHEN OTHERS THEN
                v_failed := array_append(v_failed, v_table);
            END;
        END LOOP;
        v_pending := v_failed;
    END LOOP;

    IF array_length(v_pending, 1) > 0 THEN
        RAISE EXCEPTION 'Could not purge %; tables still holding rows: %',
            v_subdomain, array_to_string(v_pending, ', ');
    END IF;

    DELETE FROM tenants WHERE id = v_tenant_id;
    GET DIAGNOSTICS v_rows = ROW_COUNT;
    v_total := v_total + v_rows;

    RAISE NOTICE 'Purged %: % rows total. REVIEW, then COMMIT or ROLLBACK.', v_subdomain, v_total;
END $$;

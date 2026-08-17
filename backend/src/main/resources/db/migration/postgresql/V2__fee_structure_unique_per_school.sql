-- fee_structures.grade_level was declared UNIQUE on its own, which is a global
-- constraint on a multi-tenant table: the first school to configure "Grade 6"
-- claimed that grade level for every school on the platform, and the second
-- school's insert failed. Nobody hit it because nothing ever wrote to this
-- table -- the admin screen that writes it lands with this migration, so the
-- constraint has to be corrected first.
--
-- Fees are also per academic year, so the same school legitimately has two
-- "Grade 6" rows once it rolls over. academic_year_id is already on the table
-- (every entity extends BaseTenantEntity); it just was not part of the key.

-- The original constraint was created inline by Hibernate's schema export and
-- therefore has a generated name that differs between databases. Look it up
-- rather than guessing, and tolerate its absence so this is safe to re-run.
DO $$
DECLARE
    constraint_name text;
BEGIN
    SELECT con.conname INTO constraint_name
      FROM pg_constraint con
     WHERE con.conrelid = 'fee_structures'::regclass
       AND con.contype = 'u'
       AND pg_get_constraintdef(con.oid) = 'UNIQUE (grade_level)';

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE fee_structures DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;

ALTER TABLE fee_structures
    ADD CONSTRAINT uk_fee_structures_tenant_year_grade
    UNIQUE (tenant_id, academic_year_id, grade_level);

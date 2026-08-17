-- H2 counterpart to the PostgreSQL V2 migration. Same intent: grade_level was
-- UNIQUE globally, so only one school on the whole platform could own a given
-- grade level. See the PostgreSQL version for the full reasoning.
--
-- The two differ in method because H2 has no DO blocks to look up the
-- generated constraint name with. Rebuilding the table sidesteps the name
-- entirely, which is acceptable here in a way it would not be against
-- production: H2 is the dev and CI database, nothing references
-- fee_structures by foreign key, and the copy below preserves any rows the
-- dev-mode seeder created.

CREATE TABLE fee_structures_v2 (
    term_fee numeric(19,2) not null,
    tuition_fee numeric(19,2) not null,
    academic_year_id uuid not null,
    id uuid not null,
    tenant_id uuid not null,
    grade_level varchar(255) not null,
    primary key (id),
    constraint uk_fee_structures_tenant_year_grade
        unique (tenant_id, academic_year_id, grade_level)
);

INSERT INTO fee_structures_v2 (term_fee, tuition_fee, academic_year_id, id, tenant_id, grade_level)
SELECT term_fee, tuition_fee, academic_year_id, id, tenant_id, grade_level FROM fee_structures;

DROP TABLE fee_structures;

ALTER TABLE fee_structures_v2 RENAME TO fee_structures;

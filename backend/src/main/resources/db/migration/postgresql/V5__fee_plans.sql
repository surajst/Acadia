-- Fees & Collections, phase 1 (ADR 0002).
--
-- fee_structures held one annual amount and nothing else, so an instalment
-- schedule was inexpressible and so was "overdue". It is replaced rather than
-- extended: the missing information was never the number, it was the timetable
-- attached to it.
--
-- Dropping the old table outright is safe only because production was emptied
-- on 2026-08-21 and nothing has been billed since. Doing this once a school has
-- a year of invoices behind it would be a migration against live money.

DROP TABLE IF EXISTS fee_structures;

-- What a grade costs for a year, and how that total is collected.
CREATE TABLE fee_plans (
    id                uuid            NOT NULL,
    tenant_id         uuid            NOT NULL,
    academic_year_id  uuid            NOT NULL,
    grade_level       varchar(255)    NOT NULL,
    annual_amount     numeric(19,2)   NOT NULL,
    PRIMARY KEY (id),
    -- Per school AND per year: a school raising its fees must not have to
    -- rewrite the row last year's invoices were priced from. grade_level alone
    -- used to be globally unique, which meant the first school to configure
    -- "Grade 6" claimed it platform-wide.
    CONSTRAINT uk_fee_plans_tenant_year_grade UNIQUE (tenant_id, academic_year_id, grade_level)
);

-- One row per instalment. How many there are, what each is worth and when each
-- falls due is entirely the school's choice -- no month, count or split is
-- assumed here or in code.
CREATE TABLE fee_plan_instalments (
    id                uuid            NOT NULL,
    tenant_id         uuid            NOT NULL,
    academic_year_id  uuid            NOT NULL,
    fee_plan_id       uuid            NOT NULL,
    sequence_number   integer         NOT NULL,
    label             varchar(255)    NOT NULL,
    amount            numeric(19,2)   NOT NULL,
    -- Days from the student's billing start, NOT a calendar date. A student
    -- admitted in September cannot owe an instalment that fell due in April, so
    -- the concrete date is resolved per student when their invoices are raised.
    due_offset_days   integer         NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_fee_plan_instalments_plan FOREIGN KEY (fee_plan_id) REFERENCES fee_plans (id),
    CONSTRAINT uk_fee_plan_instalments_seq UNIQUE (fee_plan_id, sequence_number)
);

CREATE INDEX idx_fee_plan_instalments_plan ON fee_plan_instalments (fee_plan_id);

-- Invoices gain the due date that made "overdue" computable, and a link back to
-- the instalment they came from. Both nullable: a custom invoice (phase 2)
-- belongs to no plan.
ALTER TABLE fee_invoices ADD COLUMN IF NOT EXISTS due_date date;
ALTER TABLE fee_invoices ADD COLUMN IF NOT EXISTS fee_plan_instalment_id uuid;
ALTER TABLE fee_invoices ADD COLUMN IF NOT EXISTS instalment_label varchar(255);

CREATE INDEX IF NOT EXISTS idx_fee_invoices_due_date ON fee_invoices (tenant_id, due_date);

-- When a student actually joined, so mid-year admissions are billed from the
-- right point instead of inheriting April's schedule. Nullable: existing and
-- start-of-year students fall back to the academic year's start date.
ALTER TABLE students ADD COLUMN IF NOT EXISTS admission_date date;

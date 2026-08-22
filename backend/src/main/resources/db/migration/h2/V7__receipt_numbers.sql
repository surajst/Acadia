-- H2 counterpart to the PostgreSQL V7 migration; the syntax is compatible so
-- the two agree. See that file for the reasoning.
--
-- Fees & Collections, phase 3 (ADR 0002).
--
-- A payment could be recorded but never handed a number. Schools issue
-- serially numbered receipts across the counter, and a payment record that
-- cannot be tied to the paper actually handed to a parent is not much use the
-- day someone disputes it.
--
-- Numbering resets per academic year rather than running forever: "Receipt
-- #4" means something to a school office when it is the fourth receipt this
-- year, not the four-thousandth since the system went live. Numbering is per
-- tenant AND per year for the same reason fee_plans is: two schools, or two
-- years of the same school, must not collide or interleave.
ALTER TABLE fee_transactions ADD COLUMN IF NOT EXISTS receipt_number integer;

ALTER TABLE fee_transactions
    ADD CONSTRAINT uk_fee_transactions_tenant_year_receipt
    UNIQUE (tenant_id, academic_year_id, receipt_number);

-- Reversals get no receipt of their own -- a reversal is not a new payment
-- collected across the counter, it is a correction to one already issued, and
-- issuing it a receipt number would make the sequence look like more money
-- changed hands than actually did.

-- H2 counterpart to the PostgreSQL V3 migration. See that file for the
-- reasoning; H2 supports the same ADD COLUMN IF NOT EXISTS syntax, so unlike
-- V2 the two dialects agree here.

ALTER TABLE fee_invoices ADD COLUMN IF NOT EXISTS base_amount numeric(19,2);
ALTER TABLE fee_invoices ADD COLUMN IF NOT EXISTS override_reason varchar(500);
ALTER TABLE fee_invoices ADD COLUMN IF NOT EXISTS override_by varchar(255);

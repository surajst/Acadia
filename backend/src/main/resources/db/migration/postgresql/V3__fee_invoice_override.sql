-- An invoice can now be raised at something other than the grade's fee
-- structure: sibling discounts, staff children, scholarships, mid-year joiners.
--
-- The amount alone is not enough. Once an invoice reads 14,000 where the grade
-- says 22,000, nothing distinguishes a deliberate concession from a fee change
-- since, or from a typo -- and this is money owed by a family. So the override
-- keeps what the structure said (base_amount), why it was departed from
-- (override_reason), and who decided (override_by).
--
-- All three are nullable: null base_amount means the invoice was priced
-- straight from the fee structure, which is the normal case and stays
-- untouched. Existing rows are correct as they are.

ALTER TABLE fee_invoices ADD COLUMN IF NOT EXISTS base_amount numeric(19,2);
ALTER TABLE fee_invoices ADD COLUMN IF NOT EXISTS override_reason varchar(500);
ALTER TABLE fee_invoices ADD COLUMN IF NOT EXISTS override_by varchar(255);

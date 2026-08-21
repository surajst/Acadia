-- A payment recorded at a school counter will sometimes be wrong: a digit
-- mistyped, the wrong family's invoice, a cheque that bounced. There was no way
-- to correct one -- no void, no refund, no reversal -- so the only fix was
-- editing the database by hand.
--
-- The correction is a NEW transaction that reverses the original, never an edit
-- or a delete of it. A ledger that can be rewritten is not a ledger: the
-- mistake and its correction both have to stay visible, because "we took 200,000
-- and gave 180,000 back" is a different fact from "we took 20,000".
--
-- reverses_transaction_id points at the row being undone, which is also what
-- makes double-reversal detectable. note carries the reason, required for a
-- reversal.

ALTER TABLE fee_transactions ADD COLUMN IF NOT EXISTS reverses_transaction_id uuid;
ALTER TABLE fee_transactions ADD COLUMN IF NOT EXISTS note varchar(500);

CREATE INDEX IF NOT EXISTS idx_fee_transactions_reverses
    ON fee_transactions (reverses_transaction_id);

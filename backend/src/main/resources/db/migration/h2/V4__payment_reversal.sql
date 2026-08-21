-- H2 counterpart to the PostgreSQL V4 migration. See that file for the
-- reasoning; the syntax is compatible so the two agree here.

ALTER TABLE fee_transactions ADD COLUMN IF NOT EXISTS reverses_transaction_id uuid;
ALTER TABLE fee_transactions ADD COLUMN IF NOT EXISTS note varchar(500);

CREATE INDEX IF NOT EXISTS idx_fee_transactions_reverses
    ON fee_transactions (reverses_transaction_id);

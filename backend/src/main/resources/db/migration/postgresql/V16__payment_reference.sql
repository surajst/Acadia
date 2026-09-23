-- The bank's reference for a payment: a UPI transaction id, a cheque number,
-- an NEFT UTR.
--
-- Without it a receipt cannot be matched against the bank statement, which is
-- the whole job of reconciling one. note() already exists on this table but
-- carries the reason a payment was reversed, and conflating "why this was
-- undone" with "how this arrived" would make both unreadable.
--
-- Nullable: cash has no reference, and every existing row predates the field.
alter table fee_transactions add column if not exists payment_reference varchar(64);

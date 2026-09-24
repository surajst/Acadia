-- Cancelling an invoice, which nothing could do.
--
-- An invoice raised in error had no way out. The only actions were record a
-- payment, request a waiver, or reverse a payment -- so a bill that should
-- never have existed stayed on the family's ledger and in the school's
-- outstanding total forever. A waiver is the wrong instrument: it records that
-- the school forgave a debt it was owed, which is a different fact from the
-- debt never being owed.
--
-- status is already varchar(50) and holds the enum name, so CANCELLED needs no
-- type change -- only the three columns that say who cancelled it and why. A
-- cancellation without a reason and a name against it is not an audit trail,
-- which is why the service requires both; they are nullable here because every
-- existing row predates the field.
alter table fee_invoices add column if not exists cancelled_at timestamp;
alter table fee_invoices add column if not exists cancelled_by varchar(255);
alter table fee_invoices add column if not exists cancellation_reason varchar(500);

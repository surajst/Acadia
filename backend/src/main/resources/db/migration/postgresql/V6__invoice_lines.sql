-- Fees & Collections, phase 2 (ADR 0002).
--
-- An invoice could say how much but never what for. A school trip, a
-- replacement textbook, a bus fare and a term's tuition were all just a larger
-- number, and the only way to charge for any of them was to override the
-- tuition amount -- which destroyed the ability to answer the one question a
-- parent actually asks.
--
-- Lines are deliberately free text rather than a fixed catalogue of fee heads.
-- Schools charge for things no catalogue anticipates, and a rigid list pushes
-- them straight back to abusing whichever field is nearest. A named, reusable
-- head is an optimisation to add later if the same labels keep being retyped.

CREATE TABLE invoice_lines (
    id                uuid            NOT NULL,
    tenant_id         uuid            NOT NULL,
    academic_year_id  uuid            NOT NULL,
    invoice_id        uuid            NOT NULL,
    sequence_number   integer         NOT NULL,
    description       varchar(255)    NOT NULL,
    amount            numeric(19,2)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_invoice_lines_invoice FOREIGN KEY (invoice_id) REFERENCES fee_invoices (id) ON DELETE CASCADE
);

CREATE INDEX idx_invoice_lines_invoice ON invoice_lines (invoice_id);

-- ON DELETE CASCADE deliberately, unlike fee_transactions which carries no
-- FK at all and relies on callers to delete children in the right order.
-- The dev-mode reset wipes fee_invoices directly, and RosterStudentPurger
-- deletes a student's invoices with raw SQL; neither should have to know
-- invoice_lines exists. A line has no meaning once its invoice is gone.

-- Invoices raised by hand belong to no plan and no instalment. Recording how an
-- invoice came about keeps the ledger honest about which figures a school chose
-- deliberately and which came from its fee plan.
ALTER TABLE fee_invoices ADD COLUMN IF NOT EXISTS source varchar(20);
UPDATE fee_invoices SET source = 'PLAN' WHERE source IS NULL;

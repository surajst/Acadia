# ADR 0002 — Fees & Collections

- **Status:** Accepted
- **Date:** 2026-08-22
- **Supersedes:** the `FeeStructure` + hardcoded-default arrangement described below

## Context

The first fee implementation had a single `FeeStructure` row per grade and, when
none existed, invoiced a hardcoded ₹20,000. There was no write path to
`FeeStructure` from anywhere in the UI, so in practice *every* invoice was that
constant. The number looked like configuration and behaved like a literal.

A school needs more than one amount per child per year: an annual fee split into
instalments, plus one-off charges (a trip, a replacement textbook, an exam fee)
that no plan anticipates.

## Decision

### Model

```
FeePlan ──< FeePlanInstalment
   │
   └── (generates) FeeInvoice ──< InvoiceLine
                        │
                        └──< FeeTransaction (receipt)
```

- **FeePlan** — one per (tenant, academic year, grade), enforced by
  `uk_fee_plans_tenant_year_grade`. The annual fee for that grade.
- **FeePlanInstalment** — how the annual fee is split. Due dates are stored as
  `due_offset_days` from the student's billing start, **not** calendar dates, so
  a mid-year joiner gets a schedule relative to their admission rather than
  instalments that are already overdue on day one.
- **FeeInvoice** — one instalment made concrete for one student.
- **InvoiceLine** — free-text description plus amount. Present so an admin can
  compose an invoice the plan does not cover.
- **FeeTransaction** — a payment. Carries a per-(tenant, year) `receipt_number`.

### Rules that are enforced, not documented

- **No invented amounts.** With no plan for a grade, invoicing raises
  `FeePlanMissingException` naming the grade and where to fix it. It does not
  fall back to a default. A wrong bill a parent acts on is worse than a blocked
  action an admin can resolve.
- **No overpayment.** A payment exceeding the invoice's outstanding amount is
  refused server-side.
- **Payments are never edited or deleted.** A correction is recorded as its
  opposite, and the reversed receipt stays visible on the collections report.
  The receipt was issued; the record of it has to survive.
- **Receipt numbers are gapless per school per year**, assigned as `max + 1`
  inside the payment transaction. A collision under concurrency hits the unique
  constraint and is surfaced as "try again" rather than a 500 — nothing is
  partially applied, because the invoice update shares the transaction.

### Reports

`defaulters` spans every academic year, worst-overdue first: an unpaid invoice
from last year is still owed. `collectionReport` is per year, in receipt order —
the school's day-book.

## Consequences

- A school cannot invoice until someone sets up a fee plan. This is deliberate
  friction, and the error message is the onboarding prompt.
- `due_offset_days` means a plan cannot express "always due on 15 April". If a
  school asks for fixed calendar dates, that is a new field, not a
  reinterpretation of this one.
- Custom invoices bypass the plan entirely, so they are also outside the
  plan-based expected-revenue total.

## What this ADR does not decide

- **Late fees.** Amount, flat vs percentage, grace period, and whether they
  compound are all unanswered. Nothing in the schema currently accrues them.
- **Approval thresholds.** Any admin can override an invoice amount, reverse a
  payment, or grant a concession unilaterally (only waivers require a
  principal). Every one of these writes is attributed in `audit_logs`, so the
  trail is complete — what is absent is *prevention*, i.e. a second pair of eyes
  above some value. That value is a school policy decision, not a technical one.

## Notes

`com.concept.billing` was originally a separate package and formed a dependency
cycle with `com.concept.fees`. Because `billing` was not in the ArchUnit
`MIGRATED` list, the cycle was invisible to the architecture gate. It has been
folded into `com.concept.fees`; see ADR 0001 and
`LayeringArchitectureTest.everyTopLevelPackageIsEitherEnforcedOrKnowinglyExcluded`,
which now makes an uncovered package fail rather than pass silently.

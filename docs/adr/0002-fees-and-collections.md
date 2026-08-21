# ADR 0002 — Fees & Collections

Status: Proposed · 2026-08-21

## Context

Nobody pays ₹3–5 lakh at once. Indian schools price a year and collect it in
termly instalments, and the fee model in this codebase cannot express that.

What exists today is one invoice for one annual amount:

- **`FeeInvoice` has no due date.** So an instalment schedule cannot be
  represented, and neither can "overdue" — which means no defaulters list, no
  reminders and no late fees, because none of them have anything to compute
  from.
- **`FeeStructure` is two columns**, `tuitionFee` + `termFee`. A trip, a
  replacement textbook, transport, an exam fee — none of them have anywhere to
  live.
- **`FeeTransaction` has no receipt number.** Schools issue serially numbered
  receipts; a payment record that cannot be tied to the paper handed across the
  counter is not much use in a dispute.
- **A recurring concession has to be retyped.** A sibling discount is stated as
  an invoice override, so with termly billing an admin re-enters it three times
  a year, for every year, and any one of those is a chance to get it wrong.

The workarounds all corrupt the meaning of something. Issuing a smaller invoice
for what a family can pay today silently reduces what they owe. Bending the
override into a line item destroys the ability to answer "what is this family
being billed for". Both were considered and rejected.

**Timing.** Production was emptied on 2026-08-21, so the breaking part of this
change costs nothing today: a drop and rebuild rather than a backfill. Once one
school has a year of invoices and receipts in the old shape, the same change
becomes a hard migration against live money.

## Decision

A `com.concept.billing` slice — "Fees & Collections" to users — built on four
ideas.

### 1. A plan is what a grade costs AND how it is paid

`FeeStructure` (an amount) is replaced by `FeePlan` (an amount and a schedule).
That rename is the whole idea: the missing information was never the number, it
was the timetable attached to it.

```
FeePlan            grade + academic year → annual total
  └ Instalment     ordinal · share of the total · when it falls due
```

Schools bill an **annual fee split into instalments**, not independent term
charges. An instalment is therefore a scheduled slice of an annual obligation,
which is what makes arrears, concessions and the year-end position computable
against a single total instead of being reassembled from parts.

**Due dates are data, never constants.** How many instalments, what share each
carries and when each falls due are all set by the school when it builds the
plan. No month, count or split is assumed anywhere in code: three termly
instalments and twelve monthly ones are the same structure with different rows,
and a school that collects in two unequal parts is not a special case.

This matters beyond configurability. A student admitted in September cannot owe
an instalment that fell due in April, so an instalment records **when it falls
due relative to the plan**, and the concrete date is resolved per student when
their invoices are generated — from the academic year for a student who starts
with it, and from their admission date for one who joins mid-year. Hardcoding a
calendar would have made every mid-year admission a manual correction, which is
how a school ends up billing a family for a term they were not enrolled in.

Once an invoice is generated its due date is **snapshotted onto the invoice**,
the same way the amount is. Editing a plan afterwards does not silently move a
date a family has already been told about.

### 2. Invoices carry a due date and lines

One invoice per instalment per student, each with `dueDate`, each made of
`InvoiceLine` rows (`description`, `amount`).

Lines are what let an invoice answer "what is this for". They also retire the
override-as-catch-all: a school trip is a line, not a mysteriously larger
tuition invoice.

### 3. Admins can compose a custom invoice

Not every charge belongs to a plan. An admin can raise an invoice for one
student or a group and fill in the lines themselves — label and amount, as many
as needed.

This is deliberately open rather than a fixed catalogue of fee heads. Schools
charge for things no catalogue anticipates, and a rigid list would push them
straight back to abusing whichever field is nearest. A named, reusable head is
an optimisation to add later if the same labels keep being retyped; it is not
the foundation.

### 4. Concessions are standing, not per-invoice

A sibling discount, a staff child, a scholarship — stated once against the
student, applied automatically to every instalment. The per-invoice override
stays for genuine one-offs, but it stops being the only tool.

### What carries over unchanged

Overrides, waivers, payment reversal and the overpayment guard are all correct
in this model and are kept as they are. Invoices continue to snapshot their own
amounts, so changing a plan never reprices billing already sent.

## Phasing

| Phase | Delivers | Unblocks |
|---|---|---|
| 1 | `FeePlan` + school-defined instalment schedule; invoices generated per instalment with resolved due dates | Termly billing. The actual complaint. |
| 2 | `InvoiceLine` + custom invoice builder | Trips, transport, materials |
| 3 | Numbered receipts; collection and defaulter reports | Counter workflow, chasing arrears |
| 4 | Late fees, arrears carry-forward between years | Needs 1–3 first |

Phase 1 is the breaking change and should land while production is empty.

## Consequences

**Good.** Termly billing becomes expressible, which is the difference between
this being usable by a real school and not. "Overdue" becomes computable, so
defaulters, reminders and late fees all become possible rather than each needing
its own hack. And "what is this family being billed for" gets a real answer.

**Costs.** `FeeStructure` and its screen are replaced, not extended — the fee
settings UI shipped this week is rewritten. Generating invoices per instalment
multiplies invoice volume by 3–4×, so the ledger needs to default to a filtered
view rather than listing everything.

**Risks.** Money is the least forgiving thing in the system: an invoice that is
wrong reaches a family. Every phase needs the same treatment the override and
reversal work got — refuse rather than guess, record why, keep the original.

**Not decided here.** Whether large overrides, reversals or concessions need
principal approval the way waivers do. That is a policy question about how much
one admin should be able to do alone, and it should be settled before a real
school is collecting money.

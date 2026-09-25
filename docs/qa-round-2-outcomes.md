# QA Round 2 — outcomes

Every item from the round-2 findings doc, with what changed and what tests it.
Round-2 **P0** shipped in PR #92 (merged, live); **P1–P3** are PR #93.

Status words mean what they say: *fixed* was reproduced and changed, *not
reproducible* was looked for and not found, *needs decision* is waiting on a
call that is not mine to make.

---

## P1

| Item | Status | Files changed | Tests added |
|---|---|---|---|
| **R2-P1-1** Teachers can take attendance for any section | fixed | `attendance/app/AttendanceService`, `attendance/web/AttendanceController`, `templates/attendance.html` | `AttendanceScopeTest` (6), `web/teacher_assignments.spec.js` (2) |
| **R2-P1-2** Tasks target the whole grade, not the section | fixed, **one decision flagged** | `V20` (×2 vendors), `TeacherTask`, `TeacherTaskRepository`, `TeacherTaskService`, `TasksService`, `CreateTaskRequest`, `TeacherTaskRequest`, `TaskApiController`, `SecurityConfig`, `StudentService`, `templates/teacher_tasks.html` | `TaskSectionScopeTest` (16), `web/staff_and_rewards_actions.spec.js` (1) |
| **R2-P1-3** Invoices can't be cancelled; V18 left due dates wrong | fixed | `V19` (×2 vendors), `FeeInvoice`, `FeeManagementService`, `FeeDashboardService`, `FeeDashboardView`, `InvoiceScheduleService`, `StudentFeeSummaryService`, `FeeController`, `SecurityConfig`, `templates/fee_management.html` | `InvoiceCancellationTest` (13), `DueDateRecalculationTest` (9), `web/fee_ui.spec.ts` (3) |
| **R2-P1-4** Register Student error loses the form | fixed | `StudentAdminController`, `templates/admin_management.html` | `RegisterStudentErrorTest` (7), `RegisterStudentRollbackTest` (2), `web/student_guardian.spec.ts` (3) |
| **R2-P1-5** Teacher tasks have no edit/close/delete/submissions view | fixed | `TasksService`, `TaskApiController`, `AcademicSubmissionRepository`, `SecurityConfig`, `templates/teacher_tasks.html` | `TaskManagementTest` (21), `web/teacher_task_management.spec.js` (6) |

## P2

| Item | Status | Files changed | Tests added |
|---|---|---|---|
| **R2-P2-1** Task list shows the creation date, not the due date | fixed | `app/tasks.tsx` | covered by tsc + the contrast gate; no behaviour test (display only) |
| **R2-P2-2** "Assign task" gives no feedback and stays on the form | fixed | `app/task-new.tsx` | — (see *Not verifiable here*) |
| **R2-P2-3** "1 Tasks", "1 Classes", "1 grades configured" | fixed | `utils/plural.ts` (new), `app/tasks.tsx`, `app/teacher.tsx` | the portal's half was already fixed in round 2 |
| **R2-P2-4** School name lowercase in app, uppercase on portal | fixed | `templates/fragments/shell.html` | — |
| **R2-P2-5** Stray "English" label on the empty News screen | fixed — it *is* the language control, so explained rather than removed | `app/(tabs)/announcements.tsx` | — |
| **R2-P2-6** Report card TERM tabs, nothing explains what fills a term | **partly fixed** | `app/(tabs)/performance.tsx` | — |
| **R2-P2-7** Generic Expo 404 | fixed | `app/+not-found.tsx` | — |
| **R2-P2-8** No child context for multi-child parents | **not reproducible** | none | — |
| **R2-P2-9** Unauthenticated subdomain check leaks the customer list | fixed | `config/RateLimitFilter`, `application.properties` | `RateLimitFilterTest` (+3, 13 total) |

## P3 — accessibility, naming, polish

| Item | Status | Where |
|---|---|---|
| Back-link labels leak route names | fixed | `app/_layout.tsx`, once on the root Stack |
| Action buttons not exposed as buttons | fixed | Create, Save All Scores and Hand in shipped in PR #92; Assign task and "Today's register" here |
| "Today's register" doesn't navigate | fixed — it was a summary that looked like a control; it opens attendance now | `ui/GradientHeader.tsx`, `ui/TeacherHeader.tsx`, `TeacherDashboard.tsx` |
| Empty page titles | already addressed | 13 titles on `(tabs)/_layout.tsx`, 11 on `app/_layout.tsx`, plus the new not-found |
| Guessable routes 404 (`/verify`, `/my-classes`) | fixed, by redirect | `app/verify.tsx`, `app/my-classes.tsx` (new) |
| **One name for school work** | **needs decision** | Tasks / New task / Challenges / Quests — see below |

---

## Needs a decision

**R2-P1-2, the existing task rows.** Tasks raised before `class_section_id`
existed have no section, and none can be recovered: a teacher who takes two
sections gives two candidates and no tie-break. They therefore keep reaching
the whole grade, exactly as today. The alternatives are to assign them to the
creating teacher's home class (a guess, wrong wherever a teacher takes more
than one section) or to close them (loses work children are part-way through).
`TaskSectionScopeTest` pins the current behaviour, so whichever is chosen
changes a test on purpose rather than by accident.

**R2-P2-6, hiding empty terms.** The finding offers "hide empty terms, or add an
empty-state line per term". The second is done. The first needs to know which
terms hold marks, which no endpoint reports — that is a new API, and a bigger
decision than this item.

**R2-P3, one name for school work.** Teachers see "Tasks", students get "New
task…", a "Challenges" screen, and "Quests" for parent-set goals. Picking one
word renames screens, routes, API fields and a database column, and the doc's
own suggestion ("Homework" or "Tasks") is a product choice. Nothing here
guesses at it.

---

## Not reproducible

**R2-P2-8, the child switcher.** Already implemented and wired:
`ParentDashboard.tsx` renders a switcher when a guardian has more than one
child, each chip carries `accessibilityState={{ selected }}`, `selectChild`
refetches the dashboard for that child, and the header card names the selected
child ("Aarav · Level 3") directly beneath the "Hello, Rakesh" greeting the
finding quotes. Nothing changed, because nothing here is broken.

---

## Not verifiable without a browser or a device

These were changed but could not be *observed* from here. The backend rules
behind them are tested; the rendering is not.

- **Every mobile-app change** (P2-1, P2-2, P2-5, P2-6, P2-7, P2-4's app half,
  and the P3 items). The gates that do run are `tsc`, the contrast checker and
  eslint — all clean. None of them opens a screen. **R2-P2-2 in particular
  needs a real check**: the fix is that `Alert.alert` is a no-op on React Native
  Web, and whether the new in-screen notice appears and the redirect to Tasks
  lands is exactly the kind of thing that reads correct and behaves otherwise.
- **The two route redirects** (`/verify`, `/my-classes`). Worth a cold deep link
  on a device, not just a tap from inside the app.
- **`headerBackTitle`** — what a screen reader actually announces. The setting is
  right; the announcement is the thing that matters and needs VoiceOver or
  TalkBack.
- **The empty-state card on an unassigned teacher's register** (added with
  R2-P1-1). It renders in `PageRenderSmokeTest`, but whether it reads as helpful
  rather than as a failure is a judgement only a person makes.
- **The rate limit's 429 in a real deployment.** `RateLimitFilter` is disabled
  whenever `app.dev-mode=true`, which is how CI starts the server — so the rule
  is covered by unit tests against a filter built with `devMode=false`, and
  cannot be exercised end to end here.

---

## Found while fixing, not in the findings

- **`upload.html`'s credentials CSV download has been dead.** A literal line
  break inside `join('…')` made the whole script block a syntax error, so every
  inline handler on the bulk-import page was disabled. Thymeleaf renders the
  page perfectly, which is why `PageRenderSmokeTest` passed throughout. Found by
  the new gate, not by testing.
- **A mechanical gate for that class of bug** now sits beside the existing `[[`
  check in `PageRenderSmokeTest`: no inline script may leave a quoted string open
  at a line break. It knows about block comments, template literals and regex
  literals, has zero false positives across every template, and was verified by
  reintroducing the bug.
- **`students.class_section_id` is NOT NULL**, so no student can be sectionless.
  That is what makes scoping class tasks by section safe, and it is asserted
  rather than read off the DDL.
- **A duplicate roll number is not refused** anywhere. The roll number only seeds
  a username, and a taken one yields no login rather than an error. Whether roll
  numbers should be unique per school is a separate question; nothing here
  invents that rule.
- **`TaskXpValidationTest` was silently weakened** by the new required-section
  rule — every post would have been refused for the wrong reason, so its 400
  assertions would have passed whether or not the XP rule still existed. It now
  sets up a real teacher assigned to a real section.
- **PRINCIPAL needed its own URL rule** for the two new fee actions.
  `/web/admin/**` is `hasRole("ADMIN")` and a URL matcher is evaluated before any
  `@PreAuthorize`, so the annotation would have said PRINCIPAL while the
  principal got an unexplainable 403. Third time that mismatch has been the real
  cause of a reported bug.

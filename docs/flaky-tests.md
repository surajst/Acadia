# Known flaky and untrustworthy tests

Tests that fail or pass for reasons other than the thing they claim to check.
A test on this list is worse than a missing one: a missing test is obviously
missing, and one of these will cost somebody an afternoon proving that a
failure is not theirs.

Take one off this list by fixing it, not by re-running it until it is green.

---

## `hand_in_and_gradebook.spec.js:138` — "a teacher cannot save a score above the maximum"

**Added:** 2026-09-30

**Symptom:** fails locally, passes in CI. It has failed on every local run of
the mobile suite for several sessions and has never failed in CI.

**Do not assume it is your change.** It was baselined on master with a branch's
changes reverted, same backend, same seed, and failed identically. Anyone
touching the mobile app will hit this and wonder.

**But it is not only flaky — part of it asserts nothing.** The first assertion
looks for `/out of 20/` after entering an out-of-range mark, and the failure
artefact shows that text matching the *radio button label* "Out of 20 marks"
rather than any message from the server. So that half passes whether or not the
server refuses anything.

The artefact also shows every score field reading **"out of 100"**, with other
pupils' marks already filled in (73, 78, 55...). That means the 20-mark
assessment the test creates never opened, and the panel is showing a different,
pre-seeded assessment. The most likely cause is that creating the assessment
failed and said nothing: `gradebook.tsx` reports that failure through
`Alert.alert`, which is **a no-op on React Native Web**, so the test carried on
against whatever was already on screen.

**What fixing it looks like:**

1. Assert on the server's refusal specifically -- the message names the pupil
   and the maximum -- not on a string that a control label also matches.
2. Assert the created assessment is the one open, before entering any score.
3. Surface the creation failure in the page rather than through `Alert`, the
   same way `handleSaveScores` already does. That is almost certainly the real
   bug underneath this test, and it affects users, not just the suite.

**Related:** `Alert.alert` is invisible on the web build wherever it is used;
`mobile-app/utils/notify.ts` exists for this and about thirty call sites have
not been swept.

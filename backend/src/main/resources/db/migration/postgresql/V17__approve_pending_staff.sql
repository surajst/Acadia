-- Release the staff who were invited before the invite itself became the
-- approval.
--
-- V0..V16 shipped a console where inviting a teacher created them PENDING, and
-- a Staff Registry that rendered is_active under a heading of "Status" -- so
-- the account read "Active" while login refused it as "Invalid username or
-- password". The invite path now creates APPROVED accounts, but the ones
-- already issued stayed stuck, and there was no button anywhere to release
-- them. At least one school has teachers in this state.
--
-- Only PENDING, and only staff roles. A REJECTED account was somebody's
-- deliberate decision and is left alone; students and guardians were never
-- given this status at all, and are excluded rather than relied upon not to
-- match.
--
-- Safe to re-run: after this, no staff row is PENDING, so it updates nothing.
UPDATE users
   SET approval_status = 'APPROVED'
 WHERE approval_status = 'PENDING'
   AND role IN ('ADMIN', 'PRINCIPAL', 'TEACHER', 'DRIVER');

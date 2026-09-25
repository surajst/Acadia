-- Which section a class task was set for, which nothing recorded.
--
-- teacher_tasks carries a numeric standard and nothing narrower, and a student
-- fetches class tasks with findByStandardAndAssignedToClassTrue -- so a task
-- Priya set for 6-A appears on every 6-B child's list too. It is not a logic
-- bug: there was no column to hold the answer.
--
-- Nullable, and null keeps its old meaning on purpose. Every existing row was
-- raised with no section recorded, and nothing can recover which one was meant:
-- the creating teacher's assignments do not say (a teacher who takes two
-- sections has two candidates and no tie-break), and guessing would silently
-- remove a task from children who have been looking at it. So an existing task
-- stays grade-wide, exactly as it behaves today, and every task created from
-- now on carries its section.
--
-- Deciding what should happen to those existing rows -- leave them grade-wide,
-- assign them to the teacher's home class, or close them -- is a call for the
-- school, not a default to be picked here.
alter table teacher_tasks add column if not exists class_section_id uuid;

-- The student-facing query filters on it alongside standard and tenant.
create index if not exists idx_teacher_tasks_section on teacher_tasks (class_section_id);

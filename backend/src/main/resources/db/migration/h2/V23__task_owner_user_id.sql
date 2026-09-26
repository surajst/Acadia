-- Who set a task, as a reference to the person rather than a hash of their
-- email address.
--
-- teacher_tasks.created_by_teacher_id holds UUID.nameUUIDFromBytes(email) -- an
-- MD5-based v3 UUID of the teacher's login address, with no foreign key. Task
-- ownership (TasksService.manageableTask) and the teacher's own task list both
-- key off it, so:
--
--   * a teacher who changes their email address silently loses every task they
--     have ever set, to nobody: the rows become unmanageable by anyone but an
--     admin, and nothing reports it;
--   * V15 already did this to a subset. It lowercased stored addresses, so any
--     task set before it by a user whose address was recorded in mixed case is
--     orphaned today;
--   * two writers already disagree about what the column means.
--     TeacherTaskService writes the hash; ScreenContentSeeder writes the real
--     users.id. That is why the seeded demo tasks never appear in the teacher's
--     own task list -- the reader looks up the hash and finds nothing.
--
-- Nullable, because the backfill in V24 refuses to guess: a row it cannot match
-- to a user keeps a null here and is listed rather than reassigned. The old
-- column stays for one release so a rollback has something to read.
--
-- ON DELETE SET NULL rather than the default: removing a member of staff should
-- not be blocked by work they set two years ago, and an ownerless task is
-- already a state the code has to handle.
--
-- The same text as the postgresql copy, and deliberately so: add column, add
-- constraint and create index are spelled alike in both. The file is duplicated
-- because the schema is split by vendor -- a version present in one folder and
-- missing from the other fails startup on drift.
alter table teacher_tasks
    add column if not exists created_by_user_id uuid;

alter table teacher_tasks
    drop constraint if exists fk_teacher_tasks_created_by_user;

alter table teacher_tasks
    add constraint fk_teacher_tasks_created_by_user
    foreign key (created_by_user_id) references users (id) on delete set null;

create index if not exists idx_teacher_tasks_created_by_user
    on teacher_tasks (created_by_user_id);

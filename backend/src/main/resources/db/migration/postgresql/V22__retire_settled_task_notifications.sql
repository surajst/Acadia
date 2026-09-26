-- R3-P1-3. "Waiting for you" on the app's home screen showed homework that had
-- already been handed in, and tasks that had since been deleted.
--
-- The cause was that raising a notification was only ever half the story:
-- NotificationPublisher created a TASK row when work was set and nothing ever
-- retired one, so the only way a row stopped being unread was somebody tapping
-- it. TasksService now settles them at the three moments that end a task's life
-- -- handed in, closed, deleted -- but that only helps from here on. Every row
-- already sitting unread against finished work stays on the screen QA reported.
--
-- So this clears the ones that are already settled. Read rather than deleted,
-- matching what the code does: the pupil was told, and that stays true. The
-- unread count the bell shows keys off the same column, so the two cannot
-- disagree afterwards.
--
-- Deliberately not touching anything else: a task that is still ACTIVE and not
-- handed in is genuinely waiting, whatever its due date. An overdue task is the
-- one a child most needs to still see.
UPDATE notifications
SET read = TRUE
WHERE read = FALSE
  AND type = 'TASK'
  AND related_entity_id IS NOT NULL
  AND (
    -- The task has been deleted, so the row points at nothing at all: tapping it
    -- opened a list the task was not on.
    NOT EXISTS (
        SELECT 1 FROM teacher_tasks t
        WHERE t.id = notifications.related_entity_id
    )
    -- Or closed, which already drops it off every pupil's list and refuses
    -- hand-ins, so it is waiting for nobody.
    OR EXISTS (
        SELECT 1 FROM teacher_tasks t
        WHERE t.id = notifications.related_entity_id
          AND t.task_status = 'CLOSED'
    )
    -- Or this particular pupil has already handed it in. recipient_id is a user
    -- id and submissions are keyed by student id, so the link runs through
    -- students.user_id -- which is why this is per recipient and not per task.
    --
    -- PENDING and APPROVED only: work that was sent back is an invitation to try
    -- again, and that pupil's task really is still waiting for them.
    OR EXISTS (
        SELECT 1 FROM academic_submissions s
        JOIN students st ON st.id = s.student_id
        WHERE st.user_id = notifications.recipient_id
          AND s.teacher_task_id = notifications.related_entity_id
          AND s.status IN ('PENDING', 'APPROVED')
    )
  );

-- Learning videos: a teacher points a class at a YouTube video.
--
-- youtube_id, not a URL. The id is the only part that is stable and safe to
-- rebuild a player URL from; a stored URL carries whatever tracking, playlist or
-- timestamp parameters were on the link somebody pasted, and gives the embed a
-- second source of truth that can disagree with the first. Eleven characters is
-- YouTube's own fixed width, so the column says so.
--
-- audience is one class section. Not a grade: that is the mistake teacher_tasks
-- made and V20 had to correct, and there is no reason to repeat it in a new
-- table. A video for 6-A is for 6-A.
--
-- No thumbnail column. oEmbed hands one back and storing it looked obvious, but
-- that address is a fact about YouTube's CDN at the moment the video was added,
-- and it goes stale on its own schedule -- a stored one becomes a broken image in
-- a list with nothing to say it has. It is derived from youtube_id instead.
--
-- removed_at rather than a delete. A video that has been watched has rows in
-- video_views pointing at it, and a teacher removing something from a list should
-- not take the record of who watched it with them.
create table if not exists learning_videos (
    id                  uuid primary key,
    tenant_id           uuid            not null,
    academic_year_id    uuid            not null,
    section_id          uuid            not null references class_sections (id),
    subject_code        varchar(255)    not null,
    youtube_id          varchar(11)     not null,
    title               varchar(500)    not null,
    note                varchar(1000),
    created_by_user_id  uuid            not null references users (id),
    created_at          timestamp(6)    not null,
    removed_at          timestamp(6)
);

create index if not exists idx_learning_videos_section
    on learning_videos (tenant_id, section_id);

-- Who has finished watching what.
--
-- The unique pair is the whole mechanism: the client posts on every ENDED event
-- the player fires, which for a pupil who rewinds and watches the last minute
-- again is more than once. The insert is idempotent because the database says so,
-- not because the client promises to behave.
--
-- No tenant column: a view is scoped through the video it belongs to, which is
-- tenant-scoped. The same reasoning academic_submissions already carries, and it
-- is recorded in LayeringArchitectureTest's exemption list with that reason.
create table if not exists video_views (
    id            uuid primary key,
    video_id      uuid            not null references learning_videos (id),
    student_id    uuid            not null,
    completed_at  timestamp(6)    not null,
    constraint uq_video_views_video_student unique (video_id, student_id)
);

create index if not exists idx_video_views_student on video_views (student_id);

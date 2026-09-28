-- Photographs of children, and permission to take them.
--
-- photo_consent defaults to false, and false is the honest default: a school that
-- has not recorded an answer does not have permission. It is not the same as "no"
-- -- nobody has asked yet -- but for the purpose of putting a child's face in an
-- album the two have to behave alike, because the cost of guessing wrong is not
-- symmetrical.
--
-- The field stays optional to fill in. Refusing an enrolment over a consent form
-- that has not come back yet would be worse than the gap, and the same argument
-- the emergency contact number carries: what was missing was anybody knowing.
alter table students
    add column if not exists photo_consent boolean not null default false;

-- An album is addressed at one audience: the whole school, one grade, or one
-- section. audience_ref is the grade name or the section id depending on
-- audience_type, and is null for SCHOOL.
--
-- Not a join table. An album has exactly one audience by design: "everyone in 6-A
-- and also the whole of Year 3" is two albums, and modelling it as a set invites a
-- query that forgets one of the rows.
--
-- consent_checked_by_user_id and consent_checked_at are the record that somebody
-- looked. The tick is not a formality: the uploader is shown the names of children
-- in the audience with no consent on file, and has to say they have checked none of
-- them is identifiable. Storing who and when is what makes that answerable later,
-- when it matters and the person has forgotten.
create table if not exists photo_albums (
    id                          uuid primary key,
    tenant_id                   uuid            not null,
    academic_year_id            uuid            not null,
    title                       varchar(255)    not null,
    audience_type               varchar(20)     not null
        check (audience_type in ('SCHOOL', 'GRADE', 'SECTION')),
    audience_ref                varchar(255),
    consent_checked_by_user_id  uuid            not null references users (id),
    consent_checked_at          timestamp(6)    not null,
    created_by_user_id          uuid            not null references users (id),
    created_at                  timestamp(6)    not null,
    removed_at                  timestamp(6)
);

create index if not exists idx_photo_albums_tenant on photo_albums (tenant_id, removed_at);

-- Two keys per photo, never the original. The original is what carries the
-- location the picture was taken at; see the upload path for the stripping.
--
-- removed_at rather than a delete, so a removal can be audited against the row it
-- happened to. The S3 objects do go, which is the point of removing a photograph
-- of a child -- this keeps the fact of it, not the picture.
create table if not exists album_photos (
    id                  uuid primary key,
    album_id            uuid            not null references photo_albums (id),
    thumb_key           varchar(500)    not null,
    display_key         varchar(500)    not null,
    width               integer,
    height              integer,
    uploaded_by_user_id uuid            not null references users (id),
    created_at          timestamp(6)    not null,
    removed_at          timestamp(6)
);

create index if not exists idx_album_photos_album on album_photos (album_id, removed_at);

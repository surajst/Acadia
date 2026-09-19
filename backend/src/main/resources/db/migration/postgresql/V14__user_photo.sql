-- Profile photographs.
--
-- Stored in the database rather than on disk or in object storage: Render's
-- container filesystem is ephemeral and wiped on every deploy, and there is no
-- bucket configured. The app crops to a square and downscales to ~256px before
-- upload, so a row here is tens of kilobytes, not megabytes.
--
-- In its own table, not a column on users, and that is the important part. A
-- User is loaded on nearly every authenticated request, and a byte[] on the
-- entity is fetched eagerly: @Basic(fetch = LAZY) does nothing for a basic
-- attribute unless bytecode enhancement is switched on, which it is not. A
-- separate table means the bytes are read only by the endpoint that serves
-- them.
create table user_photos (
    user_id uuid not null,
    tenant_id uuid not null,
    content_type varchar(64) not null,
    bytes bytea not null,
    updated_at timestamp not null,
    primary key (user_id)
);

-- Kept on users so the profile payload can say "there is a photo, and this is
-- how old it is" without touching the bytes. The app appends it to the photo
-- URL, so replacing a picture produces a new URL rather than a stale cache hit.
alter table users add column photo_updated_at timestamp;

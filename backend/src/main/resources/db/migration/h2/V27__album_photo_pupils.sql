-- Which children appear in which photograph.
--
-- Consent is attested per album (V26), but a parent who later withdraws it
-- needs an answer to "which photos is my child in?", and an album-level record
-- cannot give one. A row here says a member of staff identified this pupil in
-- this photo; GalleryService hides any photo showing a pupil whose
-- students.photo_consent is false from pupils and parents, so withdrawing
-- consent takes effect at once without deleting anything.
--
-- No tenant column, as for album_photos: a tag belongs to its school through
-- the photo's album, and every read joins through it.

create table if not exists album_photo_pupils (
    id                uuid primary key,
    photo_id          uuid         not null references album_photos (id),
    student_id        uuid         not null references students (id),
    tagged_by_user_id uuid         not null references users (id),
    tagged_at         timestamp(6) not null,
    constraint uq_album_photo_pupil unique (photo_id, student_id)
);

create index if not exists idx_album_photo_pupils_student on album_photo_pupils (student_id);

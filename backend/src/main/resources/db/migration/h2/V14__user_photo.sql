-- H2 counterpart of the postgresql migration of the same name; see that file
-- for why photographs live in the database and in a table of their own.
-- Identical except for the binary type: H2 spells it varbinary, Postgres bytea.
create table user_photos (
    user_id uuid not null,
    tenant_id uuid not null,
    content_type varchar(64) not null,
    bytes varbinary not null,
    updated_at timestamp(6) not null,
    primary key (user_id)
);

alter table users add column photo_updated_at timestamp(6);

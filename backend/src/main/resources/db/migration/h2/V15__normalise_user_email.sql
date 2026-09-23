-- Canonicalise the addresses already stored in mixed case.
--
-- users.email is unique and the login lookup is an exact match, so an account
-- registered as "Suraj10@gmail.com" could not be signed into as
-- "suraj10@gmail.com" or the reverse. User.setEmail now normalises every
-- write; this brings the existing rows into line so both spellings resolve to
-- the one row.
--
-- The guard matters: two rows differing only in case would collide on the
-- unique index and abort the migration, which on this deployment means the
-- application does not start. Any such pair is left exactly as it is -- still
-- reachable by its own exact spelling, as today -- rather than taking the
-- whole service down over it. Find any survivors with:
--
--   select lower(trim(email)), count(*) from users
--   group by 1 having count(*) > 1;

update users u
   set email = lower(trim(u.email))
 where u.email <> lower(trim(u.email))
   and not exists (
        select 1
          from users other
         where other.id <> u.id
           and lower(trim(other.email)) = lower(trim(u.email))
   );

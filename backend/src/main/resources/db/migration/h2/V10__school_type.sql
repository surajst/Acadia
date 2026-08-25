-- What kind of school this is: PRESCHOOL, PRIMARY, SECONDARY, K10.
--
-- Decides the words the app uses ("Level" vs "Class", "Activity Area" vs
-- "Subject") and which modules are shown. A preschool has no syllabus, no
-- marks and no homework, so those surfaces are hidden rather than translated.
--
-- Not nullable-with-no-default on purpose: every existing tenant is a
-- conventional school, and defaulting them to SECONDARY keeps their vocabulary
-- and modules exactly as they are today.
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS school_type varchar(20);

UPDATE tenants SET school_type = 'SECONDARY' WHERE school_type IS NULL;

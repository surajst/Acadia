-- Collapse school_classes into class_sections.
--
-- The two tables modelled the same thing: a grade and a section, with a room.
-- Students carried a link to both -- class_section_id NOT NULL and a nullable
-- school_class_id -- and different parts of the app read different ones. The
-- fee module took a student's grade from school_class, attendance listed
-- children by school_class, and the console listed sections. Registering a
-- child asked for a school_class and then guessed at a matching section.
--
-- That guess is the reason this migration repairs data rather than just moving
-- a column: StudentAdminService fell back to "the tenant's first section" when
-- no section matched the chosen classroom, so a child could be filed into a
-- class nobody put them in.

ALTER TABLE class_sections ADD COLUMN IF NOT EXISTS total_capacity integer;

-- 1. Every school_class needs a matching section to become. Most already have
--    one with the same grade and section name; create the rest.
INSERT INTO class_sections (id, tenant_id, academic_year_id, grade_name, section_name, room_number, total_capacity)
SELECT gen_random_uuid(), sc.tenant_id, sc.academic_year_id, sc.grade_level, sc.section_name,
       sc.room_number, sc.total_capacity
FROM school_classes sc
WHERE NOT EXISTS (
    SELECT 1 FROM class_sections cs
    WHERE cs.tenant_id = sc.tenant_id
      AND cs.grade_name = sc.grade_level
      AND cs.section_name = sc.section_name);

-- 2. Carry the capacity across for the ones that already existed.
UPDATE class_sections cs
SET total_capacity = sc.total_capacity
FROM school_classes sc
WHERE cs.tenant_id = sc.tenant_id
  AND cs.grade_name = sc.grade_level
  AND cs.section_name = sc.section_name
  AND cs.total_capacity IS NULL;

-- 3. Repair students whose section does not match the classroom they were
--    registered into. school_class is the more trustworthy of the two: it is
--    what the admin actually picked from the form, whereas class_section may
--    be whatever the fallback happened to grab.
UPDATE students st
SET class_section_id = cs.id
FROM school_classes sc, class_sections cs
WHERE st.school_class_id = sc.id
  AND cs.tenant_id = sc.tenant_id
  AND cs.grade_name = sc.grade_level
  AND cs.section_name = sc.section_name
  AND st.class_section_id <> cs.id;

-- 4. One link, not two.
ALTER TABLE students DROP COLUMN IF EXISTS school_class_id;

-- 5. Keep the rows rather than dropping them outright: nothing maps this table
--    any more, so it is out of the model, but a rename is recoverable and a
--    DROP four days before a demo is not. A later migration removes it once the
--    repair above has been seen to be right in production.
ALTER TABLE school_classes RENAME TO school_classes_replaced_by_class_sections;

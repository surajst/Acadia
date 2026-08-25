-- H2 counterpart of the postgresql migration of the same name; see that file
-- for why this exists. Written with correlated subqueries rather than
-- UPDATE ... FROM, and RANDOM_UUID() rather than gen_random_uuid(), because H2
-- supports neither of the Postgres forms.

ALTER TABLE class_sections ADD COLUMN IF NOT EXISTS total_capacity integer;

-- 1. Every school_class needs a matching section to become.
INSERT INTO class_sections (id, tenant_id, academic_year_id, grade_name, section_name, room_number, total_capacity)
SELECT RANDOM_UUID(), sc.tenant_id, sc.academic_year_id, sc.grade_level, sc.section_name,
       sc.room_number, sc.total_capacity
FROM school_classes sc
WHERE NOT EXISTS (
    SELECT 1 FROM class_sections cs
    WHERE cs.tenant_id = sc.tenant_id
      AND cs.grade_name = sc.grade_level
      AND cs.section_name = sc.section_name);

-- 2. Carry the capacity across for the ones that already existed.
UPDATE class_sections
SET total_capacity = (
        SELECT MAX(sc.total_capacity) FROM school_classes sc
        WHERE sc.tenant_id = class_sections.tenant_id
          AND sc.grade_level = class_sections.grade_name
          AND sc.section_name = class_sections.section_name)
WHERE total_capacity IS NULL
  AND EXISTS (
        SELECT 1 FROM school_classes sc
        WHERE sc.tenant_id = class_sections.tenant_id
          AND sc.grade_level = class_sections.grade_name
          AND sc.section_name = class_sections.section_name);

-- 3. Repair students filed into a section that is not the classroom they were
--    registered into.
UPDATE students
SET class_section_id = (
        SELECT MIN(cs.id) FROM class_sections cs, school_classes sc
        WHERE sc.id = students.school_class_id
          AND cs.tenant_id = sc.tenant_id
          AND cs.grade_name = sc.grade_level
          AND cs.section_name = sc.section_name)
WHERE school_class_id IS NOT NULL
  AND EXISTS (
        SELECT 1 FROM class_sections cs, school_classes sc
        WHERE sc.id = students.school_class_id
          AND cs.tenant_id = sc.tenant_id
          AND cs.grade_name = sc.grade_level
          AND cs.section_name = sc.section_name
          AND cs.id <> students.class_section_id);

-- 4. One link, not two.
ALTER TABLE students DROP COLUMN IF EXISTS school_class_id;

-- 5. Out of the model, still recoverable.
ALTER TABLE school_classes RENAME TO school_classes_replaced_by_class_sections;

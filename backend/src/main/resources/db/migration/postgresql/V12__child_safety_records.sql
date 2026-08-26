-- Child safety records. A preschool is usually required to hold these, and a
-- school of any age is asked for them the first time a child is ill or someone
-- unexpected arrives at the gate.
--
-- All nullable: the existing roster predates them, and a NOT NULL column here
-- would block editing every child already on file.
ALTER TABLE students ADD COLUMN IF NOT EXISTS date_of_birth date;
ALTER TABLE students ADD COLUMN IF NOT EXISTS medical_notes varchar(1000);
ALTER TABLE students ADD COLUMN IF NOT EXISTS emergency_contact_name varchar(255);
ALTER TABLE students ADD COLUMN IF NOT EXISTS emergency_contact_phone varchar(32);

-- Who may collect this child. A table, not a text field on the student: this
-- is a list that is added to and revoked from, and a name being removed has to
-- be as unambiguous as a name being present.
CREATE TABLE IF NOT EXISTS pickup_contacts (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    student_id uuid NOT NULL,
    name varchar(255) NOT NULL,
    relationship varchar(255),
    phone varchar(32),
    PRIMARY KEY (id),
    CONSTRAINT fk_pickup_contacts_student FOREIGN KEY (student_id)
        REFERENCES students (id) ON DELETE CASCADE
);

-- The question is always "who may collect this child", so that is the index.
CREATE INDEX IF NOT EXISTS ix_pickup_contacts_student ON pickup_contacts (student_id, tenant_id);

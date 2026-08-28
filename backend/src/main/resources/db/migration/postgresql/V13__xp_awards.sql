-- Teacher-awarded recognition.
--
-- Until now XP could only be granted by approving curriculum progress or a
-- milestone submission -- both tied to academic work a preschool does not set.
-- A nursery teacher had no way to recognise a child at all, which is why the
-- XP figures on the parent dashboard sat at zero for those schools.
--
-- A table rather than a running total on student_metrics, because the reason is
-- the point. "+10 XP" tells a parent nothing; "Helped tidy up without being
-- asked" is the thing that gets read out at the dinner table. student_metrics
-- keeps the total for display; this is the record of how it got there.
CREATE TABLE IF NOT EXISTS xp_awards (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    student_id uuid NOT NULL,
    awarded_by_user_id uuid,
    awarded_by_name varchar(255),
    badge_code varchar(64) NOT NULL,
    points integer NOT NULL,
    reason varchar(500),
    created_at timestamp NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_xp_awards_student FOREIGN KEY (student_id)
        REFERENCES students (id) ON DELETE CASCADE
);

-- Both questions this answers are per-child and newest-first: what has this
-- child been recognised for, on the profile and on the parent's phone.
CREATE INDEX IF NOT EXISTS ix_xp_awards_student ON xp_awards (student_id, tenant_id, created_at DESC);

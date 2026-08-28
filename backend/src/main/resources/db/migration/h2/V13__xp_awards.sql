-- H2 counterpart of the postgresql migration of the same name; see that file
-- for why this exists. Identical except for the index, which H2 does not accept
-- a DESC ordering on inside CREATE INDEX.
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

CREATE INDEX IF NOT EXISTS ix_xp_awards_student ON xp_awards (student_id, tenant_id, created_at);

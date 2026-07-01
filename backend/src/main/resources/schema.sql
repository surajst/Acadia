CREATE TABLE IF NOT EXISTS tenants (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    subdomain VARCHAR(255) NOT NULL UNIQUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS academic_years (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_academic_year_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    academic_year_id UUID NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_user_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_user_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years(id)
);

CREATE TABLE IF NOT EXISTS announcements (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    academic_year_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    target_grade VARCHAR(50) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_announcement_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_announcement_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years(id),
    CONSTRAINT fk_announcement_created_by FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS class_sections (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    academic_year_id UUID NOT NULL,
    grade_name VARCHAR(255) NOT NULL,
    section_name VARCHAR(255) NOT NULL,
    room_number VARCHAR(255),
    teacher_id UUID,
    CONSTRAINT fk_class_section_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_class_section_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years(id)
);

CREATE TABLE IF NOT EXISTS school_classes (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    academic_year_id UUID NOT NULL,
    grade_level VARCHAR(255) NOT NULL,
    section_name VARCHAR(255) NOT NULL,
    room_number VARCHAR(255),
    total_capacity INT NOT NULL,
    CONSTRAINT fk_school_class_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_school_class_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years(id)
);

CREATE TABLE IF NOT EXISTS students (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    academic_year_id UUID NOT NULL,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    roll_number VARCHAR(255),
    class_section_id UUID NOT NULL,
    school_class_id UUID,
    CONSTRAINT fk_student_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_student_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years(id),
    CONSTRAINT fk_student_class_section FOREIGN KEY (class_section_id) REFERENCES class_sections(id),
    CONSTRAINT fk_student_school_class FOREIGN KEY (school_class_id) REFERENCES school_classes(id)
);

CREATE TABLE IF NOT EXISTS attendance (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    academic_year_id UUID NOT NULL,
    student_id UUID NOT NULL,
    class_section_id UUID NOT NULL,
    attendance_date DATE NOT NULL,
    status VARCHAR(50) NOT NULL,
    remarks VARCHAR(255),
    CONSTRAINT fk_attendance_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_attendance_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years(id),
    CONSTRAINT fk_attendance_student FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_attendance_class_section FOREIGN KEY (class_section_id) REFERENCES class_sections(id)
);

CREATE TABLE IF NOT EXISTS parents (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    academic_year_id UUID NOT NULL,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    phone_number VARCHAR(255),
    email VARCHAR(255),
    CONSTRAINT fk_parent_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_parent_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years(id)
);

CREATE TABLE IF NOT EXISTS student_parents (
    student_id UUID NOT NULL,
    parent_id UUID NOT NULL,
    PRIMARY KEY (student_id, parent_id),
    CONSTRAINT fk_sp_student FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_sp_parent FOREIGN KEY (parent_id) REFERENCES parents(id)
);

CREATE TABLE IF NOT EXISTS math_chapters (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    academic_year_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    sequence_number INT NOT NULL,
    CONSTRAINT fk_math_chapter_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_math_chapter_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years(id)
);

CREATE TABLE IF NOT EXISTS math_skills (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    academic_year_id UUID NOT NULL,
    skill_name VARCHAR(255) NOT NULL,
    max_xp_reward INT NOT NULL,
    chapter_id UUID NOT NULL,
    CONSTRAINT fk_math_skill_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_math_skill_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years(id),
    CONSTRAINT fk_math_skill_chapter FOREIGN KEY (chapter_id) REFERENCES math_chapters(id)
);

CREATE TABLE IF NOT EXISTS student_metrics (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    academic_year_id UUID NOT NULL,
    student_id UUID NOT NULL,
    school_xp INT NOT NULL DEFAULT 0,
    parent_xp INT NOT NULL DEFAULT 0,
    active_streak INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_student_metric_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_student_metric_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years(id),
    CONSTRAINT fk_student_metric_student FOREIGN KEY (student_id) REFERENCES students(id)
);

CREATE TABLE IF NOT EXISTS parent_quests (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    academic_year_id UUID NOT NULL,
    parent_id UUID NOT NULL,
    student_id UUID NOT NULL,
    task_description VARCHAR(255) NOT NULL,
    xp_bounty INT NOT NULL,
    status VARCHAR(50) NOT NULL,
    CONSTRAINT fk_parent_quest_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_parent_quest_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years(id),
    CONSTRAINT fk_parent_quest_parent FOREIGN KEY (parent_id) REFERENCES parents(id),
    CONSTRAINT fk_parent_quest_student FOREIGN KEY (student_id) REFERENCES students(id)
);

CREATE TABLE IF NOT EXISTS parent_rewards (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    academic_year_id UUID NOT NULL,
    parent_id UUID NOT NULL,
    student_id UUID NOT NULL,
    reward_title VARCHAR(255) NOT NULL,
    xp_cost INT NOT NULL,
    status VARCHAR(50) NOT NULL,
    CONSTRAINT fk_parent_reward_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_parent_reward_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years(id),
    CONSTRAINT fk_parent_reward_parent FOREIGN KEY (parent_id) REFERENCES parents(id),
    CONSTRAINT fk_parent_reward_student FOREIGN KEY (student_id) REFERENCES students(id)
);

CREATE TABLE IF NOT EXISTS fee_structures (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    academic_year_id UUID NOT NULL,
    grade_level VARCHAR(255) NOT NULL UNIQUE,
    tuition_fee DECIMAL(19, 2) NOT NULL,
    term_fee DECIMAL(19, 2) NOT NULL,
    CONSTRAINT fk_fee_structure_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_fee_structure_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years(id)
);

CREATE TABLE IF NOT EXISTS fee_invoices (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    academic_year_id UUID NOT NULL,
    student_id UUID NOT NULL,
    total_amount DECIMAL(19, 2) NOT NULL,
    amount_paid DECIMAL(19, 2) NOT NULL,
    amount_due DECIMAL(19, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    CONSTRAINT fk_fee_invoice_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_fee_invoice_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years(id),
    CONSTRAINT fk_fee_invoice_student FOREIGN KEY (student_id) REFERENCES students(id)
);

CREATE TABLE IF NOT EXISTS fee_transactions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    academic_year_id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    amount_paid DECIMAL(19, 2) NOT NULL,
    payment_mode VARCHAR(50) NOT NULL,
    paid_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_fee_transaction_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_fee_transaction_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years(id),
    CONSTRAINT fk_fee_transaction_invoice FOREIGN KEY (invoice_id) REFERENCES fee_invoices(id)
);

CREATE TABLE IF NOT EXISTS curriculums (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    academic_year_id UUID NOT NULL,
    syllabus_type VARCHAR(50) NOT NULL,
    standard INT NOT NULL,
    subject_type VARCHAR(50) NOT NULL,
    topic_name VARCHAR(255) NOT NULL,
    topic_order INT NOT NULL DEFAULT 0,
    xp_reward INT NOT NULL DEFAULT 50,
    CONSTRAINT fk_curriculum_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_curriculum_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years(id)
);

CREATE TABLE IF NOT EXISTS student_progress (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL,
    curriculum_id UUID NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMP WITHOUT TIME ZONE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    rejection_reason VARCHAR(255),
    CONSTRAINT fk_student_progress_student FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_student_progress_curriculum FOREIGN KEY (curriculum_id) REFERENCES curriculums(id)
);

CREATE TABLE IF NOT EXISTS assessments (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    academic_year_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    subject_type VARCHAR(50) NOT NULL,
    class_section_id UUID NOT NULL,
    term VARCHAR(20) NOT NULL,
    max_score INT NOT NULL,
    assessment_date DATE NOT NULL,
    created_by_teacher_id UUID NOT NULL,
    CONSTRAINT fk_assessment_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_assessment_class_section FOREIGN KEY (class_section_id) REFERENCES class_sections(id)
);

CREATE TABLE IF NOT EXISTS student_assessment_scores (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL,
    assessment_id UUID NOT NULL,
    score INT NOT NULL,
    graded_by_teacher_id UUID NOT NULL,
    graded_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_score_student FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_score_assessment FOREIGN KEY (assessment_id) REFERENCES assessments(id)
);

CREATE TABLE IF NOT EXISTS teacher_tasks (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    subject_type VARCHAR(50) NOT NULL,
    task_type VARCHAR(50) NOT NULL,
    standard INT NOT NULL,
    assigned_to_class BOOLEAN NOT NULL DEFAULT TRUE,
    student_id UUID,
    created_by_teacher_id UUID NOT NULL,
    xp_reward INT NOT NULL DEFAULT 50,
    due_date DATE,
    task_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    question_1 VARCHAR(500),
    question_2 VARCHAR(500),
    question_3 VARCHAR(500)
);



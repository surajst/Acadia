--
-- PostgreSQL database dump
--

\restrict jh1ZnCks78VUjDimquf2jrbqIJ0ZCadQZ86WKUiqDiDAynBWzFXRP0f41wGePdo

-- Dumped from database version 18.4 (Debian 18.4-1.pgdg12+1)
-- Dumped by pg_dump version 18.4

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: acadia
--

-- *not* creating schema, since initdb creates it


ALTER SCHEMA public OWNER TO acadia;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: academic_submissions; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.academic_submissions (
    id uuid NOT NULL,
    answer_1 character varying(1000),
    answer_2 character varying(1000),
    answer_3 character varying(1000),
    proof_of_work_notes character varying(2000),
    rejection_reason character varying(255),
    skill_name character varying(255) NOT NULL,
    status character varying(255),
    student_id uuid NOT NULL,
    submitted_at timestamp(6) without time zone,
    teacher_task_id uuid,
    xp_bounty integer NOT NULL
);


ALTER TABLE public.academic_submissions OWNER TO acadia;

--
-- Name: academic_years; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.academic_years (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    is_current boolean DEFAULT false NOT NULL
);


ALTER TABLE public.academic_years OWNER TO acadia;

--
-- Name: announcements; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.announcements (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    title character varying(255) NOT NULL,
    content text NOT NULL,
    target_grade character varying(50) NOT NULL,
    created_by uuid NOT NULL,
    created_at timestamp without time zone NOT NULL
);


ALTER TABLE public.announcements OWNER TO acadia;

--
-- Name: assessments; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.assessments (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    title character varying(255) NOT NULL,
    subject_type character varying(50) NOT NULL,
    class_section_id uuid NOT NULL,
    term character varying(20) NOT NULL,
    max_score integer NOT NULL,
    assessment_date date NOT NULL,
    created_by_teacher_id uuid NOT NULL
);


ALTER TABLE public.assessments OWNER TO acadia;

--
-- Name: attendance; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.attendance (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    student_id uuid NOT NULL,
    class_section_id uuid NOT NULL,
    attendance_date date NOT NULL,
    status character varying(50) NOT NULL,
    remarks character varying(255)
);


ALTER TABLE public.attendance OWNER TO acadia;

--
-- Name: audit_logs; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.audit_logs (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    actor_user_id uuid,
    actor_email character varying(255),
    action character varying(100) NOT NULL,
    entity_type character varying(100),
    entity_id uuid,
    summary character varying(500) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


ALTER TABLE public.audit_logs OWNER TO acadia;

--
-- Name: bus_routes; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.bus_routes (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    driver_id uuid,
    current_latitude double precision,
    current_longitude double precision,
    last_ping_at timestamp without time zone
);


ALTER TABLE public.bus_routes OWNER TO acadia;

--
-- Name: class_sections; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.class_sections (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    grade_name character varying(255) NOT NULL,
    section_name character varying(255) NOT NULL,
    room_number character varying(255),
    teacher_id uuid,
    bus_route_id uuid
);


ALTER TABLE public.class_sections OWNER TO acadia;

--
-- Name: conversations; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.conversations (
    id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    last_message_at timestamp(6) without time zone NOT NULL,
    student_id uuid NOT NULL,
    teacher_id uuid NOT NULL
);


ALTER TABLE public.conversations OWNER TO acadia;

--
-- Name: curriculums; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.curriculums (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    syllabus_type character varying(50) NOT NULL,
    standard integer NOT NULL,
    subject_type character varying(50) NOT NULL,
    topic_name character varying(255) NOT NULL,
    topic_order integer DEFAULT 0 NOT NULL,
    xp_reward integer DEFAULT 50 NOT NULL
);


ALTER TABLE public.curriculums OWNER TO acadia;

--
-- Name: fee_invoices; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.fee_invoices (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    student_id uuid NOT NULL,
    total_amount numeric(19,2) NOT NULL,
    amount_paid numeric(19,2) NOT NULL,
    amount_due numeric(19,2) NOT NULL,
    status character varying(50) NOT NULL,
    waiver_amount numeric(19,2),
    waiver_reason character varying(500),
    waiver_status character varying(20),
    CONSTRAINT fee_invoices_waiver_status_check CHECK (((waiver_status)::text = ANY ((ARRAY['NONE'::character varying, 'PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])))
);


ALTER TABLE public.fee_invoices OWNER TO acadia;

--
-- Name: fee_structures; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.fee_structures (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    grade_level character varying(255) NOT NULL,
    tuition_fee numeric(19,2) NOT NULL,
    term_fee numeric(19,2) NOT NULL
);


ALTER TABLE public.fee_structures OWNER TO acadia;

--
-- Name: fee_transactions; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.fee_transactions (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    invoice_id uuid NOT NULL,
    amount_paid numeric(19,2) NOT NULL,
    payment_mode character varying(50) NOT NULL,
    paid_at timestamp without time zone NOT NULL
);


ALTER TABLE public.fee_transactions OWNER TO acadia;

--
-- Name: grade_subjects; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.grade_subjects (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    grade_name character varying(100) NOT NULL,
    subject_id uuid NOT NULL
);


ALTER TABLE public.grade_subjects OWNER TO acadia;

--
-- Name: math_chapters; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.math_chapters (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    title character varying(255) NOT NULL,
    sequence_number integer NOT NULL
);


ALTER TABLE public.math_chapters OWNER TO acadia;

--
-- Name: math_skills; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.math_skills (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    skill_name character varying(255) NOT NULL,
    max_xp_reward integer NOT NULL,
    chapter_id uuid NOT NULL
);


ALTER TABLE public.math_skills OWNER TO acadia;

--
-- Name: messages; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.messages (
    id uuid NOT NULL,
    body character varying(2000) NOT NULL,
    conversation_id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    sender_id uuid NOT NULL,
    sender_role character varying(255) NOT NULL
);


ALTER TABLE public.messages OWNER TO acadia;

--
-- Name: notifications; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.notifications (
    id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    body character varying(1000),
    created_at timestamp(6) without time zone NOT NULL,
    read boolean NOT NULL,
    recipient_id uuid NOT NULL,
    recipient_role character varying(255) NOT NULL,
    related_entity_id uuid,
    title character varying(255) NOT NULL,
    type character varying(255) NOT NULL
);


ALTER TABLE public.notifications OWNER TO acadia;

--
-- Name: parent_quests; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.parent_quests (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    parent_id uuid NOT NULL,
    student_id uuid NOT NULL,
    task_description character varying(255) NOT NULL,
    xp_bounty integer NOT NULL,
    status character varying(50) NOT NULL
);


ALTER TABLE public.parent_quests OWNER TO acadia;

--
-- Name: parent_rewards; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.parent_rewards (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    parent_id uuid NOT NULL,
    student_id uuid NOT NULL,
    reward_title character varying(255) NOT NULL,
    xp_cost integer NOT NULL,
    status character varying(50) NOT NULL
);


ALTER TABLE public.parent_rewards OWNER TO acadia;

--
-- Name: parents; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.parents (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    first_name character varying(255) NOT NULL,
    last_name character varying(255) NOT NULL,
    phone_number character varying(255),
    email character varying(255),
    user_id uuid,
    preferred_language character varying(255)
);


ALTER TABLE public.parents OWNER TO acadia;

--
-- Name: reward_items; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.reward_items (
    id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    description character varying(1000),
    display_emoji character varying(255),
    inventory_count integer NOT NULL,
    title character varying(255) NOT NULL,
    xp_cost integer NOT NULL
);


ALTER TABLE public.reward_items OWNER TO acadia;

--
-- Name: school_classes; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.school_classes (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    grade_level character varying(255) NOT NULL,
    section_name character varying(255) NOT NULL,
    room_number character varying(255),
    total_capacity integer NOT NULL
);


ALTER TABLE public.school_classes OWNER TO acadia;

--
-- Name: student_assessment_scores; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.student_assessment_scores (
    id uuid NOT NULL,
    student_id uuid NOT NULL,
    assessment_id uuid NOT NULL,
    score integer NOT NULL,
    graded_by_teacher_id uuid NOT NULL,
    graded_at timestamp without time zone NOT NULL
);


ALTER TABLE public.student_assessment_scores OWNER TO acadia;

--
-- Name: student_metrics; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.student_metrics (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    student_id uuid NOT NULL,
    school_xp integer DEFAULT 0 NOT NULL,
    parent_xp integer DEFAULT 0 NOT NULL,
    active_streak integer DEFAULT 0 NOT NULL
);


ALTER TABLE public.student_metrics OWNER TO acadia;

--
-- Name: student_parents; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.student_parents (
    student_id uuid NOT NULL,
    parent_id uuid NOT NULL
);


ALTER TABLE public.student_parents OWNER TO acadia;

--
-- Name: student_progress; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.student_progress (
    id uuid NOT NULL,
    student_id uuid NOT NULL,
    curriculum_id uuid NOT NULL,
    completed boolean DEFAULT false NOT NULL,
    completed_at timestamp without time zone,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    rejection_reason character varying(255)
);


ALTER TABLE public.student_progress OWNER TO acadia;

--
-- Name: students; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.students (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    first_name character varying(255) NOT NULL,
    last_name character varying(255) NOT NULL,
    roll_number character varying(255),
    class_section_id uuid NOT NULL,
    school_class_id uuid,
    user_id uuid
);


ALTER TABLE public.students OWNER TO acadia;

--
-- Name: subject_assignments; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.subject_assignments (
    id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    is_home_class boolean NOT NULL,
    subject_name character varying(255) NOT NULL,
    class_section_id uuid NOT NULL,
    teacher_id uuid NOT NULL
);


ALTER TABLE public.subject_assignments OWNER TO acadia;

--
-- Name: subjects; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.subjects (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    code character varying(50) NOT NULL,
    display_name character varying(100) NOT NULL,
    color_hex character varying(20),
    active boolean DEFAULT true NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL
);


ALTER TABLE public.subjects OWNER TO acadia;

--
-- Name: teacher_tasks; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.teacher_tasks (
    id uuid NOT NULL,
    tenant_id uuid,
    academic_year_id uuid,
    title character varying(255) NOT NULL,
    description text,
    subject_type character varying(50) NOT NULL,
    task_type character varying(50) NOT NULL,
    standard integer NOT NULL,
    assigned_to_class boolean DEFAULT true NOT NULL,
    student_id uuid,
    created_by_teacher_id uuid NOT NULL,
    xp_reward integer DEFAULT 50 NOT NULL,
    due_date date,
    task_status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    question_1 character varying(500),
    question_2 character varying(500),
    question_3 character varying(500)
);


ALTER TABLE public.teacher_tasks OWNER TO acadia;

--
-- Name: teacher_verifications; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.teacher_verifications (
    id uuid NOT NULL,
    evaluated_at timestamp(6) without time zone,
    evaluated_by uuid,
    skill_id uuid NOT NULL,
    status character varying(20) NOT NULL,
    student_id uuid NOT NULL,
    submitted_at timestamp(6) without time zone NOT NULL,
    tenant_id uuid NOT NULL
);


ALTER TABLE public.teacher_verifications OWNER TO acadia;

--
-- Name: tenants; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.tenants (
    id uuid NOT NULL,
    name character varying(255) NOT NULL,
    subdomain character varying(255) NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    tier character varying(20),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    onboarding_completed boolean
);


ALTER TABLE public.tenants OWNER TO acadia;

--
-- Name: timetable_entries; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.timetable_entries (
    id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    day_of_week character varying(3) NOT NULL,
    end_time character varying(5) NOT NULL,
    period_number integer NOT NULL,
    room_number character varying(255),
    start_time character varying(5) NOT NULL,
    subject_name character varying(255) NOT NULL,
    teacher_id uuid NOT NULL,
    class_section_id uuid NOT NULL
);


ALTER TABLE public.timetable_entries OWNER TO acadia;

--
-- Name: users; Type: TABLE; Schema: public; Owner: acadia
--

CREATE TABLE public.users (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    email character varying(255) NOT NULL,
    password_hash character varying(255) NOT NULL,
    full_name character varying(255) NOT NULL,
    role character varying(50) NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    approval_status character varying(20),
    CONSTRAINT users_approval_status_check CHECK (((approval_status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])))
);


ALTER TABLE public.users OWNER TO acadia;

--
-- Name: academic_submissions academic_submissions_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.academic_submissions
    ADD CONSTRAINT academic_submissions_pkey PRIMARY KEY (id);


--
-- Name: academic_years academic_years_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.academic_years
    ADD CONSTRAINT academic_years_pkey PRIMARY KEY (id);


--
-- Name: announcements announcements_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.announcements
    ADD CONSTRAINT announcements_pkey PRIMARY KEY (id);


--
-- Name: assessments assessments_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.assessments
    ADD CONSTRAINT assessments_pkey PRIMARY KEY (id);


--
-- Name: attendance attendance_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.attendance
    ADD CONSTRAINT attendance_pkey PRIMARY KEY (id);


--
-- Name: audit_logs audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (id);


--
-- Name: bus_routes bus_routes_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.bus_routes
    ADD CONSTRAINT bus_routes_pkey PRIMARY KEY (id);


--
-- Name: class_sections class_sections_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.class_sections
    ADD CONSTRAINT class_sections_pkey PRIMARY KEY (id);


--
-- Name: conversations conversations_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.conversations
    ADD CONSTRAINT conversations_pkey PRIMARY KEY (id);


--
-- Name: curriculums curriculums_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.curriculums
    ADD CONSTRAINT curriculums_pkey PRIMARY KEY (id);


--
-- Name: fee_invoices fee_invoices_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.fee_invoices
    ADD CONSTRAINT fee_invoices_pkey PRIMARY KEY (id);


--
-- Name: fee_structures fee_structures_grade_level_key; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.fee_structures
    ADD CONSTRAINT fee_structures_grade_level_key UNIQUE (grade_level);


--
-- Name: fee_structures fee_structures_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.fee_structures
    ADD CONSTRAINT fee_structures_pkey PRIMARY KEY (id);


--
-- Name: fee_transactions fee_transactions_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.fee_transactions
    ADD CONSTRAINT fee_transactions_pkey PRIMARY KEY (id);


--
-- Name: grade_subjects grade_subjects_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.grade_subjects
    ADD CONSTRAINT grade_subjects_pkey PRIMARY KEY (id);


--
-- Name: math_chapters math_chapters_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.math_chapters
    ADD CONSTRAINT math_chapters_pkey PRIMARY KEY (id);


--
-- Name: math_skills math_skills_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.math_skills
    ADD CONSTRAINT math_skills_pkey PRIMARY KEY (id);


--
-- Name: messages messages_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.messages
    ADD CONSTRAINT messages_pkey PRIMARY KEY (id);


--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);


--
-- Name: parent_quests parent_quests_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.parent_quests
    ADD CONSTRAINT parent_quests_pkey PRIMARY KEY (id);


--
-- Name: parent_rewards parent_rewards_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.parent_rewards
    ADD CONSTRAINT parent_rewards_pkey PRIMARY KEY (id);


--
-- Name: parents parents_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.parents
    ADD CONSTRAINT parents_pkey PRIMARY KEY (id);


--
-- Name: reward_items reward_items_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.reward_items
    ADD CONSTRAINT reward_items_pkey PRIMARY KEY (id);


--
-- Name: school_classes school_classes_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.school_classes
    ADD CONSTRAINT school_classes_pkey PRIMARY KEY (id);


--
-- Name: student_assessment_scores student_assessment_scores_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.student_assessment_scores
    ADD CONSTRAINT student_assessment_scores_pkey PRIMARY KEY (id);


--
-- Name: student_metrics student_metrics_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.student_metrics
    ADD CONSTRAINT student_metrics_pkey PRIMARY KEY (id);


--
-- Name: student_parents student_parents_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.student_parents
    ADD CONSTRAINT student_parents_pkey PRIMARY KEY (student_id, parent_id);


--
-- Name: student_progress student_progress_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.student_progress
    ADD CONSTRAINT student_progress_pkey PRIMARY KEY (id);


--
-- Name: students students_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.students
    ADD CONSTRAINT students_pkey PRIMARY KEY (id);


--
-- Name: subject_assignments subject_assignments_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.subject_assignments
    ADD CONSTRAINT subject_assignments_pkey PRIMARY KEY (id);


--
-- Name: subjects subjects_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.subjects
    ADD CONSTRAINT subjects_pkey PRIMARY KEY (id);


--
-- Name: teacher_tasks teacher_tasks_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.teacher_tasks
    ADD CONSTRAINT teacher_tasks_pkey PRIMARY KEY (id);


--
-- Name: teacher_verifications teacher_verifications_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.teacher_verifications
    ADD CONSTRAINT teacher_verifications_pkey PRIMARY KEY (id);


--
-- Name: tenants tenants_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT tenants_pkey PRIMARY KEY (id);


--
-- Name: tenants tenants_subdomain_key; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT tenants_subdomain_key UNIQUE (subdomain);


--
-- Name: timetable_entries timetable_entries_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.timetable_entries
    ADD CONSTRAINT timetable_entries_pkey PRIMARY KEY (id);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: subject_assignments fk9fjwnmi6jxjo6mkf5bfrd47; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.subject_assignments
    ADD CONSTRAINT fk9fjwnmi6jxjo6mkf5bfrd47 FOREIGN KEY (class_section_id) REFERENCES public.class_sections(id);


--
-- Name: academic_years fk_academic_year_tenant; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.academic_years
    ADD CONSTRAINT fk_academic_year_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: announcements fk_announcement_academic_year; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.announcements
    ADD CONSTRAINT fk_announcement_academic_year FOREIGN KEY (academic_year_id) REFERENCES public.academic_years(id);


--
-- Name: announcements fk_announcement_created_by; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.announcements
    ADD CONSTRAINT fk_announcement_created_by FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: announcements fk_announcement_tenant; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.announcements
    ADD CONSTRAINT fk_announcement_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: assessments fk_assessment_class_section; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.assessments
    ADD CONSTRAINT fk_assessment_class_section FOREIGN KEY (class_section_id) REFERENCES public.class_sections(id);


--
-- Name: assessments fk_assessment_tenant; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.assessments
    ADD CONSTRAINT fk_assessment_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: attendance fk_attendance_academic_year; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.attendance
    ADD CONSTRAINT fk_attendance_academic_year FOREIGN KEY (academic_year_id) REFERENCES public.academic_years(id);


--
-- Name: attendance fk_attendance_class_section; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.attendance
    ADD CONSTRAINT fk_attendance_class_section FOREIGN KEY (class_section_id) REFERENCES public.class_sections(id);


--
-- Name: attendance fk_attendance_student; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.attendance
    ADD CONSTRAINT fk_attendance_student FOREIGN KEY (student_id) REFERENCES public.students(id);


--
-- Name: attendance fk_attendance_tenant; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.attendance
    ADD CONSTRAINT fk_attendance_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: audit_logs fk_audit_log_tenant; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT fk_audit_log_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: bus_routes fk_bus_route_academic_year; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.bus_routes
    ADD CONSTRAINT fk_bus_route_academic_year FOREIGN KEY (academic_year_id) REFERENCES public.academic_years(id);


--
-- Name: bus_routes fk_bus_route_tenant; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.bus_routes
    ADD CONSTRAINT fk_bus_route_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: class_sections fk_class_section_academic_year; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.class_sections
    ADD CONSTRAINT fk_class_section_academic_year FOREIGN KEY (academic_year_id) REFERENCES public.academic_years(id);


--
-- Name: class_sections fk_class_section_tenant; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.class_sections
    ADD CONSTRAINT fk_class_section_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: curriculums fk_curriculum_academic_year; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.curriculums
    ADD CONSTRAINT fk_curriculum_academic_year FOREIGN KEY (academic_year_id) REFERENCES public.academic_years(id);


--
-- Name: curriculums fk_curriculum_tenant; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.curriculums
    ADD CONSTRAINT fk_curriculum_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: fee_invoices fk_fee_invoice_academic_year; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.fee_invoices
    ADD CONSTRAINT fk_fee_invoice_academic_year FOREIGN KEY (academic_year_id) REFERENCES public.academic_years(id);


--
-- Name: fee_invoices fk_fee_invoice_student; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.fee_invoices
    ADD CONSTRAINT fk_fee_invoice_student FOREIGN KEY (student_id) REFERENCES public.students(id);


--
-- Name: fee_invoices fk_fee_invoice_tenant; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.fee_invoices
    ADD CONSTRAINT fk_fee_invoice_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: fee_structures fk_fee_structure_academic_year; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.fee_structures
    ADD CONSTRAINT fk_fee_structure_academic_year FOREIGN KEY (academic_year_id) REFERENCES public.academic_years(id);


--
-- Name: fee_structures fk_fee_structure_tenant; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.fee_structures
    ADD CONSTRAINT fk_fee_structure_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: fee_transactions fk_fee_transaction_academic_year; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.fee_transactions
    ADD CONSTRAINT fk_fee_transaction_academic_year FOREIGN KEY (academic_year_id) REFERENCES public.academic_years(id);


--
-- Name: fee_transactions fk_fee_transaction_invoice; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.fee_transactions
    ADD CONSTRAINT fk_fee_transaction_invoice FOREIGN KEY (invoice_id) REFERENCES public.fee_invoices(id);


--
-- Name: fee_transactions fk_fee_transaction_tenant; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.fee_transactions
    ADD CONSTRAINT fk_fee_transaction_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: grade_subjects fk_grade_subject_subject; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.grade_subjects
    ADD CONSTRAINT fk_grade_subject_subject FOREIGN KEY (subject_id) REFERENCES public.subjects(id);


--
-- Name: grade_subjects fk_grade_subject_tenant; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.grade_subjects
    ADD CONSTRAINT fk_grade_subject_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: math_chapters fk_math_chapter_academic_year; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.math_chapters
    ADD CONSTRAINT fk_math_chapter_academic_year FOREIGN KEY (academic_year_id) REFERENCES public.academic_years(id);


--
-- Name: math_chapters fk_math_chapter_tenant; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.math_chapters
    ADD CONSTRAINT fk_math_chapter_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: math_skills fk_math_skill_academic_year; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.math_skills
    ADD CONSTRAINT fk_math_skill_academic_year FOREIGN KEY (academic_year_id) REFERENCES public.academic_years(id);


--
-- Name: math_skills fk_math_skill_chapter; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.math_skills
    ADD CONSTRAINT fk_math_skill_chapter FOREIGN KEY (chapter_id) REFERENCES public.math_chapters(id);


--
-- Name: math_skills fk_math_skill_tenant; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.math_skills
    ADD CONSTRAINT fk_math_skill_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: parents fk_parent_academic_year; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.parents
    ADD CONSTRAINT fk_parent_academic_year FOREIGN KEY (academic_year_id) REFERENCES public.academic_years(id);


--
-- Name: parent_quests fk_parent_quest_academic_year; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.parent_quests
    ADD CONSTRAINT fk_parent_quest_academic_year FOREIGN KEY (academic_year_id) REFERENCES public.academic_years(id);


--
-- Name: parent_quests fk_parent_quest_parent; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.parent_quests
    ADD CONSTRAINT fk_parent_quest_parent FOREIGN KEY (parent_id) REFERENCES public.parents(id);


--
-- Name: parent_quests fk_parent_quest_student; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.parent_quests
    ADD CONSTRAINT fk_parent_quest_student FOREIGN KEY (student_id) REFERENCES public.students(id);


--
-- Name: parent_quests fk_parent_quest_tenant; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.parent_quests
    ADD CONSTRAINT fk_parent_quest_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: parent_rewards fk_parent_reward_academic_year; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.parent_rewards
    ADD CONSTRAINT fk_parent_reward_academic_year FOREIGN KEY (academic_year_id) REFERENCES public.academic_years(id);


--
-- Name: parent_rewards fk_parent_reward_parent; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.parent_rewards
    ADD CONSTRAINT fk_parent_reward_parent FOREIGN KEY (parent_id) REFERENCES public.parents(id);


--
-- Name: parent_rewards fk_parent_reward_student; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.parent_rewards
    ADD CONSTRAINT fk_parent_reward_student FOREIGN KEY (student_id) REFERENCES public.students(id);


--
-- Name: parent_rewards fk_parent_reward_tenant; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.parent_rewards
    ADD CONSTRAINT fk_parent_reward_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: parents fk_parent_tenant; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.parents
    ADD CONSTRAINT fk_parent_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: school_classes fk_school_class_academic_year; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.school_classes
    ADD CONSTRAINT fk_school_class_academic_year FOREIGN KEY (academic_year_id) REFERENCES public.academic_years(id);


--
-- Name: school_classes fk_school_class_tenant; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.school_classes
    ADD CONSTRAINT fk_school_class_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: student_assessment_scores fk_score_assessment; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.student_assessment_scores
    ADD CONSTRAINT fk_score_assessment FOREIGN KEY (assessment_id) REFERENCES public.assessments(id);


--
-- Name: student_assessment_scores fk_score_student; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.student_assessment_scores
    ADD CONSTRAINT fk_score_student FOREIGN KEY (student_id) REFERENCES public.students(id);


--
-- Name: student_parents fk_sp_parent; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.student_parents
    ADD CONSTRAINT fk_sp_parent FOREIGN KEY (parent_id) REFERENCES public.parents(id);


--
-- Name: student_parents fk_sp_student; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.student_parents
    ADD CONSTRAINT fk_sp_student FOREIGN KEY (student_id) REFERENCES public.students(id);


--
-- Name: students fk_student_academic_year; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.students
    ADD CONSTRAINT fk_student_academic_year FOREIGN KEY (academic_year_id) REFERENCES public.academic_years(id);


--
-- Name: students fk_student_class_section; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.students
    ADD CONSTRAINT fk_student_class_section FOREIGN KEY (class_section_id) REFERENCES public.class_sections(id);


--
-- Name: student_metrics fk_student_metric_academic_year; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.student_metrics
    ADD CONSTRAINT fk_student_metric_academic_year FOREIGN KEY (academic_year_id) REFERENCES public.academic_years(id);


--
-- Name: student_metrics fk_student_metric_student; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.student_metrics
    ADD CONSTRAINT fk_student_metric_student FOREIGN KEY (student_id) REFERENCES public.students(id);


--
-- Name: student_metrics fk_student_metric_tenant; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.student_metrics
    ADD CONSTRAINT fk_student_metric_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: student_progress fk_student_progress_curriculum; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.student_progress
    ADD CONSTRAINT fk_student_progress_curriculum FOREIGN KEY (curriculum_id) REFERENCES public.curriculums(id);


--
-- Name: student_progress fk_student_progress_student; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.student_progress
    ADD CONSTRAINT fk_student_progress_student FOREIGN KEY (student_id) REFERENCES public.students(id);


--
-- Name: students fk_student_school_class; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.students
    ADD CONSTRAINT fk_student_school_class FOREIGN KEY (school_class_id) REFERENCES public.school_classes(id);


--
-- Name: students fk_student_tenant; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.students
    ADD CONSTRAINT fk_student_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: subjects fk_subject_tenant; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.subjects
    ADD CONSTRAINT fk_subject_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: users fk_user_academic_year; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT fk_user_academic_year FOREIGN KEY (academic_year_id) REFERENCES public.academic_years(id);


--
-- Name: users fk_user_tenant; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT fk_user_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: timetable_entries fkg91nwufnfo4ltakuy150lo37k; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.timetable_entries
    ADD CONSTRAINT fkg91nwufnfo4ltakuy150lo37k FOREIGN KEY (class_section_id) REFERENCES public.class_sections(id);


--
-- Name: subject_assignments fkk9qq0y3xdxwe0dyvrvddlvm9a; Type: FK CONSTRAINT; Schema: public; Owner: acadia
--

ALTER TABLE ONLY public.subject_assignments
    ADD CONSTRAINT fkk9qq0y3xdxwe0dyvrvddlvm9a FOREIGN KEY (teacher_id) REFERENCES public.users(id);


--
-- Name: DEFAULT PRIVILEGES FOR SEQUENCES; Type: DEFAULT ACL; Schema: -; Owner: postgres
--

ALTER DEFAULT PRIVILEGES FOR ROLE postgres GRANT ALL ON SEQUENCES TO acadia;


--
-- Name: DEFAULT PRIVILEGES FOR TYPES; Type: DEFAULT ACL; Schema: -; Owner: postgres
--

ALTER DEFAULT PRIVILEGES FOR ROLE postgres GRANT ALL ON TYPES TO acadia;


--
-- Name: DEFAULT PRIVILEGES FOR FUNCTIONS; Type: DEFAULT ACL; Schema: -; Owner: postgres
--

ALTER DEFAULT PRIVILEGES FOR ROLE postgres GRANT ALL ON FUNCTIONS TO acadia;


--
-- Name: DEFAULT PRIVILEGES FOR TABLES; Type: DEFAULT ACL; Schema: -; Owner: postgres
--

ALTER DEFAULT PRIVILEGES FOR ROLE postgres GRANT ALL ON TABLES TO acadia;


--
-- PostgreSQL database dump complete
--

\unrestrict jh1ZnCks78VUjDimquf2jrbqIJ0ZCadQZ86WKUiqDiDAynBWzFXRP0f41wGePdo


-- Actions an admin may ask for but not carry out alone.
--
-- Reversing a payment un-records cash the school already receipted, and
-- editing a fee plan re-prices a whole grade at once. Both were single-admin
-- actions, audited but ungated. They now park here until a principal decides.
--
-- The requested change is stored as JSON rather than applied to a draft row:
-- these two actions are wholesale replacements, so replaying the payload at
-- approval time produces exactly the requested end state, and nothing has
-- touched the live data in the meantime.
CREATE TABLE IF NOT EXISTS approval_requests (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    action varchar(40) NOT NULL,
    payload_json text NOT NULL,
    summary varchar(500) NOT NULL,
    requested_by_user_id uuid,
    requested_by_email varchar(255),
    requested_at timestamp(6) NOT NULL,
    status varchar(20) NOT NULL,
    decided_by_user_id uuid,
    decided_at timestamp(6),
    decision_reason varchar(500),
    PRIMARY KEY (id)
);

-- The principal's queue is "what is pending for my school", so that is the
-- index. Ordered by request time because the oldest request is the one that
-- has been holding someone up longest.
CREATE INDEX IF NOT EXISTS ix_approval_requests_tenant_status
    ON approval_requests (tenant_id, status, requested_at);

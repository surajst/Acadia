-- Records who asked for a waiver, so the same person cannot also grant it.
-- Until now only the audit log knew, and nothing read it back: an admin could
-- request a waiver and approve it two clicks later, because the approve gate
-- is hasAnyRole('ADMIN','PRINCIPAL') and ADMIN is in both sets.
ALTER TABLE fee_invoices ADD COLUMN IF NOT EXISTS waiver_requested_by_user_id uuid;

-- Backfill from the audit trail rather than leaving existing pending requests
-- unattributed -- an unknown requester cannot be compared against, so those
-- rows would skip the new check entirely. Most recent request wins, since a
-- re-request supersedes the earlier one.
UPDATE fee_invoices
SET waiver_requested_by_user_id = (
        SELECT al.actor_user_id
        FROM audit_logs al
        WHERE al.entity_id = fee_invoices.id
          AND al.action = 'FEE_WAIVER_REQUESTED'
        ORDER BY al.created_at DESC
        LIMIT 1)
WHERE waiver_status = 'PENDING'
  AND waiver_requested_by_user_id IS NULL;

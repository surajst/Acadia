package com.concept.fees.data;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.concept.common.TenantScopedRepository;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface FeeInvoiceRepository extends TenantScopedRepository<FeeInvoice, UUID> {
    List<FeeInvoice> findByStudentId(UUID studentId);

    /**
     * Invoices for a set of children, confined to one tenant.
     *
     * <p>The tenant is in the query rather than assumed from the caller's
     * student ids: {@link #findByStudentId} takes an id and no tenant, so it
     * returns rows for any student in any school that happens to match. The
     * parent portal reads this, and a fee ledger is exactly the kind of thing
     * that must never cross a school boundary.
     */
    List<FeeInvoice> findByStudentIdInAndTenantId(Collection<UUID> studentIds, UUID tenantId);
    List<FeeInvoice> findByTenantId(UUID tenantId);
    Page<FeeInvoice> findByTenantId(UUID tenantId, Pageable pageable);
    List<FeeInvoice> findByTenantIdAndWaiverStatus(UUID tenantId, FeeInvoice.FeeWaiverStatus waiverStatus);
}

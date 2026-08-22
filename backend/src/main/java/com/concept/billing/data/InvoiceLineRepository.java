package com.concept.billing.data;

import com.concept.common.TenantScopedRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface InvoiceLineRepository extends TenantScopedRepository<InvoiceLine, UUID> {

    List<InvoiceLine> findByInvoiceIdAndTenantIdOrderBySequenceNumberAsc(UUID invoiceId, UUID tenantId);

    /** Batch lookup so the ledger does not issue one query per row. */
    List<InvoiceLine> findByInvoiceIdInAndTenantIdOrderBySequenceNumberAsc(
            Collection<UUID> invoiceIds, UUID tenantId);
}

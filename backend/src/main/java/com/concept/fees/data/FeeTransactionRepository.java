package com.concept.fees.data;

import org.springframework.data.jpa.repository.JpaRepository;
import com.concept.common.TenantScopedRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface FeeTransactionRepository extends TenantScopedRepository<FeeTransaction, UUID> {
    List<FeeTransaction> findByInvoiceId(UUID invoiceId);

    List<FeeTransaction> findByInvoiceIdAndTenantIdOrderByPaidAtAsc(UUID invoiceId, UUID tenantId);

    /** A payment already undone must not be undone twice. */
    boolean existsByReversesTransactionId(UUID reversesTransactionId);
}

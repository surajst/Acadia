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

    /**
     * Every payment against a set of invoices, newest first, confined to one
     * tenant. The parent portal reads this to show a family their own receipt
     * history; {@link #findByInvoiceId} takes no tenant and is not safe for
     * that. Reversals come back too -- a parent who saw a receipt appear and
     * then vanish is owed the row that explains it.
     */
    List<FeeTransaction> findByInvoiceIdInAndTenantIdOrderByPaidAtDesc(
            java.util.Collection<UUID> invoiceIds, UUID tenantId);

    /** A payment already undone must not be undone twice. */
    boolean existsByReversesTransactionId(UUID reversesTransactionId);

    /**
     * The highest receipt number issued so far this year, so the next payment
     * can be numbered one past it. Nullable return: no receipts yet means the
     * next one is #1.
     */
    @org.springframework.data.jpa.repository.Query(
        "select max(t.receiptNumber) from FeeTransaction t "
      + "where t.tenantId = :tenantId and t.academicYearId = :academicYearId")
    Integer findMaxReceiptNumber(UUID tenantId, UUID academicYearId);

    java.util.Optional<FeeTransaction> findByTenantIdAndAcademicYearIdAndReceiptNumber(
            UUID tenantId, UUID academicYearId, Integer receiptNumber);

    /** For the school's own record: every receipt issued this year, in order. */
    List<FeeTransaction> findByTenantIdAndAcademicYearIdAndReceiptNumberIsNotNullOrderByReceiptNumberAsc(
            UUID tenantId, UUID academicYearId);
}

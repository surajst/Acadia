package com.schoolos.management;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface FeeTransactionRepository extends JpaRepository<FeeTransaction, UUID> {
    List<FeeTransaction> findByInvoiceId(UUID invoiceId);
}

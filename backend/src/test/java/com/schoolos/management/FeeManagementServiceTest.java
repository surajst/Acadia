package com.schoolos.management;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class FeeManagementServiceTest {

    @Autowired
    private FeeManagementService feeManagementService;

    @Autowired
    private FeeInvoiceRepository feeInvoiceRepository;

    @Autowired
    private FeeTransactionRepository feeTransactionRepository;

    private UUID testInvoiceId;

    @BeforeEach
    public void setup() {
        // Create a test invoice
        FeeInvoice invoice = new FeeInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setStudentId(UUID.randomUUID());
        invoice.setTotalAmount(new BigDecimal("20000.00"));
        invoice.setAmountPaid(BigDecimal.ZERO);
        
        invoice.setTenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"));
        invoice.setAcademicYearId(UUID.fromString("00000000-0000-0000-0000-111111111111"));
        
        invoice.updateBalances();
        FeeInvoice saved = feeInvoiceRepository.saveAndFlush(invoice);
        testInvoiceId = saved.getId();
    }

    @Test
    public void testRecordPayment_UnpaidToPartiallyPaid() {
        // Record a partial payment of 5000 INR
        feeManagementService.recordPayment(testInvoiceId, new BigDecimal("5000.00"), "ONLINE", null);

        FeeInvoice updated = feeInvoiceRepository.findById(testInvoiceId).orElseThrow();
        assertEquals(new BigDecimal("5000.00"), updated.getAmountPaid());
        assertEquals(new BigDecimal("15000.00"), updated.getAmountDue());
        assertEquals(FeeInvoice.FeeStatus.PARTIALLY_PAID, updated.getStatus());

        List<FeeTransaction> txns = feeTransactionRepository.findByInvoiceId(testInvoiceId);
        assertEquals(1, txns.size());
        assertEquals(new BigDecimal("5000.00"), txns.get(0).getAmountPaid());
        assertEquals("ONLINE", txns.get(0).getPaymentMode());
    }

    @Test
    public void testRecordPayment_FullyPaid() {
        // Record a payment of 20000 INR
        feeManagementService.recordPayment(testInvoiceId, new BigDecimal("20000.00"), "CASH", null);

        FeeInvoice updated = feeInvoiceRepository.findById(testInvoiceId).orElseThrow();
        assertEquals(new BigDecimal("20000.00"), updated.getAmountPaid());
        assertEquals(BigDecimal.ZERO.setScale(2), updated.getAmountDue());
        assertEquals(FeeInvoice.FeeStatus.PAID, updated.getStatus());

        List<FeeTransaction> txns = feeTransactionRepository.findByInvoiceId(testInvoiceId);
        assertEquals(1, txns.size());
        assertEquals(new BigDecimal("20000.00"), txns.get(0).getAmountPaid());
        assertEquals("CASH", txns.get(0).getPaymentMode());
    }

    @Test
    public void testRecordPayment_NegativeAmountThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            feeManagementService.recordPayment(testInvoiceId, new BigDecimal("-100.00"), "CHECK", null);
        });
    }

    @Test
    public void testRecordPayment_InvalidInvoiceThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            feeManagementService.recordPayment(UUID.randomUUID(), new BigDecimal("100.00"), "CASH", null);
        });
    }
}

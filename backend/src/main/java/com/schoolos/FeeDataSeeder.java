package com.schoolos;

import com.schoolos.management.FeeManagementService;
import com.schoolos.management.FeeInvoiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3)
public class FeeDataSeeder implements CommandLineRunner {

    @Autowired
    private FeeManagementService feeManagementService;

    @Autowired
    private FeeInvoiceRepository feeInvoiceRepository;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("\n--- STARTING FEE DATA SEEDER ---");
        long invoiceCount = feeInvoiceRepository.count();
        if (invoiceCount == 0) {
            System.out.println(">> Fee Invoice count is 0. Initializing baseline invoices...");
            feeManagementService.initializeInvoices();
        } else {
            System.out.println(">> Fee Invoices already exist (" + invoiceCount + "). Skipping seeding.");
        }
        System.out.println("--- FEE DATA SEEDER COMPLETED ---\n");
    }
}

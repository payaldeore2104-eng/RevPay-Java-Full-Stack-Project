package com.revpay.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
public class InvoiceScheduler {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Update overdue invoices every night at midnight.
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void updateOverdueInvoices() {
        System.out.println("[InvoiceScheduler] Running scheduled task to update OVERDUE invoices.");
        int rowsUpdated = jdbcTemplate.update(
                "UPDATE invoice SET status = 'OVERDUE' WHERE status = 'SENT' AND due_date < TRUNC(SYSDATE)");
        System.out.println("[InvoiceScheduler] Marked " + rowsUpdated + " invoices as OVERDUE.");
    }

    /**
     * Run on startup to catch any missed updates in case the application was down
     * at midnight.
     */
    @PostConstruct
    public void init() {
        updateOverdueInvoices();
    }
}

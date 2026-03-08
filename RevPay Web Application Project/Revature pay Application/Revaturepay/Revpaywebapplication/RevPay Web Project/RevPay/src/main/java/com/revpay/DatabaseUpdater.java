package com.revpay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseUpdater implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseUpdater.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        addColumnIfMissing("cards", "cvv", "VARCHAR2(10)");
        addColumnIfMissing("cards", "billing_address", "VARCHAR2(255)");
        addColumnIfMissing("cards", "card_pin", "VARCHAR2(255)");
        addColumnIfMissing("cards", "card_type", "VARCHAR2(20)");
        addColumnIfMissing("bank_accounts", "account_type", "VARCHAR2(20) DEFAULT 'SAVINGS'");
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_tab_columns WHERE table_name = ? AND column_name = ?",
                    Integer.class,
                    table.toUpperCase(),
                    column.toUpperCase());
            if (count != null && count == 0) {
                jdbcTemplate.execute("ALTER TABLE " + table + " ADD " + column + " " + definition);
                logger.info("[DB Migration] Added column '{}.{}' successfully.", table, column);
            } else {
                logger.debug("[DB Migration] Column '{}.{}' already exists. Skipping.", table, column);
            }
        } catch (Exception e) {
            logger.warn("[DB Migration] Could not check/add column '{}.{}': {}", table, column, e.getMessage());
        }
    }
}

package com.project;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CardSchemaMigration {
    private final JdbcTemplate jdbc;
    public CardSchemaMigration(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PostConstruct
    public void ensureCardStatusColumn() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'CREDIT_CARDS' AND COLUMN_NAME = 'CARD_STATUS'",
            Integer.class);
        if (count != null && count == 0) {
            jdbc.execute("ALTER TABLE CREDIT_CARDS ADD (CARD_STATUS VARCHAR2(16) DEFAULT 'ACTIVE' NOT NULL)");
        }
        jdbc.update("UPDATE CREDIT_CARDS SET CARD_STATUS = 'ACTIVE' WHERE CARD_STATUS IS NULL");
    }
}

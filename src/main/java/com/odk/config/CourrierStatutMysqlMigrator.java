package com.odk.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Sur MySQL, une colonne {@code ENUM} ou {@code VARCHAR} trop courte provoque
 * {@code Data truncated for column 'statut'} lors des nouveaux statuts courrier.
 * Élargit {@code statut} en VARCHAR(64) une fois au démarrage si nécessaire.
 */
@Component
@ConditionalOnProperty(prefix = "app.db", name = "auto-migrate-courrier-statut", havingValue = "true", matchIfMissing = true)
public class CourrierStatutMysqlMigrator {

    private static final Logger log = LoggerFactory.getLogger(CourrierStatutMysqlMigrator.class);

    private static final String[] TABLES = { "courrier", "historique_courrier", "reponse_courrier" };

    private final DataSource dataSource;

    @Value("${spring.datasource.url:}")
    private String jdbcUrl;

    public CourrierStatutMysqlMigrator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Order(50)
    @EventListener(ApplicationReadyEvent.class)
    public void widenStatutColumnsIfNeeded() {
        if (jdbcUrl == null || !jdbcUrl.toLowerCase().contains("mysql")) {
            return;
        }
        for (String table : TABLES) {
            try {
                if (!tableNeedsMigration(table)) {
                    continue;
                }
                try (Connection conn = dataSource.getConnection()) {
                    try (var st = conn.createStatement()) {
                        st.execute("ALTER TABLE `" + table + "` MODIFY COLUMN `statut` VARCHAR(64) NOT NULL");
                    } catch (Exception first) {
                        try (var st2 = conn.createStatement()) {
                            st2.execute("ALTER TABLE `" + table + "` MODIFY COLUMN `statut` VARCHAR(64)");
                        }
                        log.debug("statut migré sans contrainte NOT NULL pour {} : {}", table, first.getMessage());
                    }
                    log.info("Migration MySQL : table {} — colonne statut passée en VARCHAR(64).", table);
                }
            } catch (Exception e) {
                log.warn(
                        "Impossible d'élargir {}.statut (droits ALTER absents ou colonne absente). Exécutez manuellement db/manual-alter-courrier-statut-mysql.sql — {}",
                        table,
                        e.getMessage());
            }
        }
    }

    private boolean tableNeedsMigration(String table) throws Exception {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT COLUMN_TYPE FROM INFORMATION_SCHEMA.COLUMNS "
                                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = 'statut'")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                String columnType = rs.getString(1);
                if (columnType == null) {
                    return false;
                }
                String t = columnType.trim().toLowerCase();
                if (t.startsWith("enum(")) {
                    return true;
                }
                if (t.startsWith("varchar(")) {
                    int open = t.indexOf('(');
                    int close = t.indexOf(')');
                    if (open > 0 && close > open) {
                        int len = Integer.parseInt(t.substring(open + 1, close).trim());
                        return len < 64;
                    }
                }
                return false;
            }
        }
    }
}

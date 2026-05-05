package com.wirs.inventory.reservation.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Verifies that all required database tables exist and seed data is present.
 * Uses PostgreSQL via Testcontainers.
 */
@Tag("integration")
class LiquibaseMigrationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void allTablesExistAfterMigration() throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery(
                 "SELECT table_name FROM information_schema.tables "
                 + "WHERE table_schema = 'public'")) {
            var tables = new ArrayList<String>();
            while (rs.next()) {
                tables.add(rs.getString("table_name"));
            }
            assertThat(tables).contains(
                "products", "inventory", "reservations",
                "reservation_items", "reservation_events", "reservation_expiry_state"
            );
        }
    }

    @Test
    void expiryCoordinationRowExists() throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery(
                 "SELECT id FROM reservation_expiry_state WHERE id = 1")) {
            assertThat(rs.next()).isTrue();
        }
    }

    @Test
    void seedDataPresent() throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            var rs = stmt.executeQuery("SELECT COUNT(*) FROM products");
            rs.next();
            assertThat(rs.getLong(1)).isGreaterThanOrEqualTo(5);

            rs = stmt.executeQuery(
                "SELECT COUNT(*) FROM inventory WHERE available_stock > 0");
            rs.next();
            assertThat(rs.getLong(1)).isGreaterThanOrEqualTo(5);
        }
    }
}

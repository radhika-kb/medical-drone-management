package com.medicaldrone.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Existing Workbench databases may predate open supplier requests and still
 * require pickup coordinates before a supplier has accepted a request.
 */
@Component
public class OpenSupplierRequestsSchemaInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(OpenSupplierRequestsSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public OpenSupplierRequestsSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        String databaseProduct = jdbcTemplate.execute(
                (ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName());
        String normalizedProduct = databaseProduct == null ? "" : databaseProduct.toLowerCase(Locale.ROOT);
        if (!(normalizedProduct.contains("mysql") || normalizedProduct.contains("mariadb"))) {
            return;
        }

        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                        "WHERE table_schema = DATABASE() AND table_name = 'medical_requests'",
                Integer.class);
        if (tableCount == null || tableCount == 0) {
            logger.warn("Skipped open-request schema compatibility check: medical_requests table was not found.");
            return;
        }

        makeNullableIfPresent("pickup_lat");
        makeNullableIfPresent("pickup_lng");
    }

    private void makeNullableIfPresent(String columnName) {
        String nullable = jdbcTemplate.query(
                "SELECT is_nullable FROM information_schema.columns " +
                        "WHERE table_schema = DATABASE() AND table_name = 'medical_requests' AND column_name = ?",
                resultSet -> resultSet.next() ? resultSet.getString("is_nullable") : null,
                columnName);
        if (nullable == null) {
            logger.warn("Skipped nullable update for medical_requests.{} because the column was not found.", columnName);
            return;
        }
        if ("YES".equalsIgnoreCase(nullable)) {
            return;
        }

        jdbcTemplate.execute("ALTER TABLE medical_requests MODIFY COLUMN " + columnName + " DOUBLE NULL");
        logger.info("Ensured medical_requests.{} accepts NULL for requests awaiting a supplier.", columnName);
    }
}

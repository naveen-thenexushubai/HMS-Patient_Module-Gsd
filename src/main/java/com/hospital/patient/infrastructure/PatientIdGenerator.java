package com.hospital.patient.infrastructure;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Custom Hibernate ID generator for Patient IDs in P2026001 format.
 * Format: P{YEAR}{5-digit sequence}
 * Example: P2026000001, P2026000002, etc.
 */
public class PatientIdGenerator implements IdentifierGenerator {

    private static final String VALUE_PREFIX = "P";
    private static final String NUMBER_FORMAT = "%05d";
    private static final String SEQUENCE_QUERY = "SELECT nextval('patient_seq')";

    @Override
    public Object generate(SharedSessionContractImplementor session, Object object) {
        try {
            Connection connection = session.getJdbcConnectionAccess().obtainConnection();
            try (PreparedStatement ps = connection.prepareStatement(SEQUENCE_QUERY);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long sequenceValue = rs.getLong(1);
                    int year = LocalDate.now().getYear();
                    return String.format("%s%d%s", VALUE_PREFIX, year, String.format(NUMBER_FORMAT, sequenceValue));
                }
                throw new RuntimeException("Unable to generate patient ID from sequence");
            } finally {
                session.getJdbcConnectionAccess().releaseConnection(connection);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to generate patient ID", e);
        }
    }
}

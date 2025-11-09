package com.example.webui.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DenialRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private DenialRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        createTestSchema(dataSource);
        repository = new DenialRepositoryImpl(jdbcTemplate);
    }

    @Test
    void shouldFindAllDenials() throws Exception {
        insertDenial(dataSource, "CP1", "USER1", "ZONE1", "ZONE2", "ACCESS_DENIED");
        insertDenial(dataSource, "CP2", "USER2", "ZONE2", "ZONE1", "STATE_MISMATCH");

        List<DenialView> result = repository.findAll(0, 10);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).checkpointCode()).isIn("CP1", "CP2");
        assertThat(result.get(0).reason()).isIn("ACCESS_DENIED", "STATE_MISMATCH");
    }

    @Test
    void shouldCountDenials() throws Exception {
        insertDenial(dataSource, "CP1", "USER1", "ZONE1", "ZONE2", "ACCESS_DENIED");
        insertDenial(dataSource, "CP2", "USER2", "ZONE2", "ZONE1", "STATE_MISMATCH");

        long count = repository.count();

        assertThat(count).isEqualTo(2);
    }

    private void insertDenial(DataSource dataSource, String checkpointCode, String userCode,
                             String fromZoneCode, String toZoneCode, String reason) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO denials (checkpoint_code, user_code, from_zone_code, to_zone_code, reason) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, checkpointCode);
            ps.setString(2, userCode);
            ps.setString(3, fromZoneCode);
            ps.setString(4, toZoneCode);
            ps.setString(5, reason);
            ps.executeUpdate();
        }
    }
}


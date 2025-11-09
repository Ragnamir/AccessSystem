package com.example.webui.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private EventRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        createTestSchema(dataSource);
        repository = new EventRepositoryImpl(jdbcTemplate);
    }

    @Test
    void shouldFindRecentEvents() throws Exception {
        UUID userId = insertUser(dataSource, "USER1");
        UUID zone1Id = insertZone(dataSource, "ZONE1");
        UUID zone2Id = insertZone(dataSource, "ZONE2");
        UUID checkpointId = insertCheckpoint(dataSource, "CP1", zone1Id, zone2Id);
        
        insertEvent(dataSource, "EVENT1", checkpointId, userId, zone1Id, zone2Id, Instant.now());
        insertEvent(dataSource, "EVENT2", checkpointId, userId, zone2Id, zone1Id, Instant.now().minusSeconds(10));

        List<EventView> result = repository.findRecent(0, 10);

        assertThat(result).hasSize(2);
        // Events should be sorted by timestamp DESC (most recent first)
        assertThat(result.get(0).eventTimestamp()).isAfter(result.get(1).eventTimestamp());
    }

    @Test
    void shouldCountEvents() throws Exception {
        UUID userId = insertUser(dataSource, "USER1");
        UUID zone1Id = insertZone(dataSource, "ZONE1");
        UUID zone2Id = insertZone(dataSource, "ZONE2");
        UUID checkpointId = insertCheckpoint(dataSource, "CP1", zone1Id, zone2Id);
        
        insertEvent(dataSource, "EVENT1", checkpointId, userId, zone1Id, zone2Id, Instant.now());
        insertEvent(dataSource, "EVENT2", checkpointId, userId, zone2Id, zone1Id, Instant.now());

        long count = repository.count();

        assertThat(count).isEqualTo(2);
    }

    private void insertEvent(DataSource dataSource, String eventId, UUID checkpointId, UUID userId,
                            UUID fromZoneId, UUID toZoneId, Instant timestamp) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO events (event_id, checkpoint_id, user_id, from_zone_id, to_zone_id, event_timestamp) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, eventId);
            ps.setObject(2, checkpointId);
            ps.setObject(3, userId);
            ps.setObject(4, fromZoneId);
            ps.setObject(5, toZoneId);
            ps.setTimestamp(6, Timestamp.from(timestamp));
            ps.executeUpdate();
        }
    }
}


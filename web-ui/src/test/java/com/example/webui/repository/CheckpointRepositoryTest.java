package com.example.webui.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CheckpointRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private CheckpointRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        createTestSchema(dataSource);
        repository = new CheckpointRepositoryImpl(jdbcTemplate);
    }

    @Test
    void shouldFindAllCheckpoints() throws Exception {
        UUID zone1Id = insertZone(dataSource, "ZONE1");
        UUID zone2Id = insertZone(dataSource, "ZONE2");
        insertCheckpoint(dataSource, "CP1", zone1Id, zone2Id);
        insertCheckpoint(dataSource, "CP2", zone2Id, zone1Id);

        List<CheckpointView> result = repository.findAll(0, 10);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).code()).isIn("CP1", "CP2");
    }

    @Test
    void shouldCountCheckpoints() throws Exception {
        UUID zone1Id = insertZone(dataSource, "ZONE1");
        UUID zone2Id = insertZone(dataSource, "ZONE2");
        insertCheckpoint(dataSource, "CP1", zone1Id, zone2Id);
        insertCheckpoint(dataSource, "CP2", zone2Id, zone1Id);

        long count = repository.count();

        assertThat(count).isEqualTo(2);
    }

    @Test
    void shouldSupportPagination() throws Exception {
        UUID zone1Id = insertZone(dataSource, "ZONE1");
        UUID zone2Id = insertZone(dataSource, "ZONE2");
        insertCheckpoint(dataSource, "CP1", zone1Id, zone2Id);
        insertCheckpoint(dataSource, "CP2", zone2Id, zone1Id);
        insertCheckpoint(dataSource, "CP3", zone1Id, zone2Id);

        List<CheckpointView> page1 = repository.findAll(0, 2);
        List<CheckpointView> page2 = repository.findAll(2, 2);

        assertThat(page1).hasSize(2);
        assertThat(page2).hasSize(1);
    }
}


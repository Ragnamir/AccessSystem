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

class UserRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UserRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        createTestSchema(dataSource);
        repository = new UserRepositoryImpl(jdbcTemplate);
    }

    @Test
    void shouldFindAllUsersWithState() throws Exception {
        UUID userId1 = insertUser(dataSource, "USER1");
        UUID userId2 = insertUser(dataSource, "USER2");
        UUID zoneId = insertZone(dataSource, "ZONE1");
        
        // Set state for first user
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO user_state (user_id, current_zone_id) VALUES (?, ?)")) {
            ps.setObject(1, userId1);
            ps.setObject(2, zoneId);
            ps.executeUpdate();
        }

        List<UserView> result = repository.findAllWithState(0, 10);

        assertThat(result).hasSize(2);
        UserView user1 = result.stream().filter(u -> u.code().equals("USER1")).findFirst().orElseThrow();
        assertThat(user1.currentZoneCode()).isEqualTo("ZONE1");
        UserView user2 = result.stream().filter(u -> u.code().equals("USER2")).findFirst().orElseThrow();
        assertThat(user2.currentZoneCode()).isNull();
    }

    @Test
    void shouldCountUsers() throws Exception {
        insertUser(dataSource, "USER1");
        insertUser(dataSource, "USER2");

        long count = repository.count();

        assertThat(count).isEqualTo(2);
    }
}


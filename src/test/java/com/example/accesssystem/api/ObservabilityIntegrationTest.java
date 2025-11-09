package com.example.accesssystem.api;

import com.example.accesssystem.domain.DenialReason;
import com.example.accesssystem.service.DenialRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class ObservabilityIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13-alpine");

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DenialRepository denialRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void setup() {
        // Clean and ensure minimal data for health checks
        jdbcTemplate.update("DELETE FROM issuer_keys");
        jdbcTemplate.update("DELETE FROM checkpoint_keys");

        // Insert at least one key for each to make keys health UP
        jdbcTemplate.update("INSERT INTO issuer_keys (id, issuer_code, public_key_pem) VALUES ('00000000-0000-0000-0000-000000000001', 'issuer-x', 'pem')");
        jdbcTemplate.update("INSERT INTO checkpoint_keys (id, checkpoint_code, public_key_pem) VALUES ('00000000-0000-0000-0000-000000000002', 'cp-x', 'pem')");
    }

    @Test
    void health_and_metrics_should_be_available() {
        String base = "http://localhost:" + port + "/actuator";

        // Health endpoint
        ResponseEntity<Map> health = restTemplate.getForEntity(base + "/health", Map.class);
        assertThat(health.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(health.getBody()).isNotNull();
        assertThat(health.getBody().get("status")).isEqualTo("UP");

        // Keys health component present
        Map components = (Map) health.getBody().get("components");
        assertThat(components).isNotNull();
        Map keys = (Map) components.get("keys");
        assertThat(keys).isNotNull();
        assertThat(keys.get("status")).isEqualTo("UP");

        // Increment denial metric via repository
        denialRepository.recordDenial("cp-x", DenialReason.ACCESS_DENIED, "test");

        // Metrics endpoint for denials
        ResponseEntity<Map> denialsMetric = restTemplate.getForEntity(base + "/metrics/access_denials_total", Map.class);
        assertThat(denialsMetric.getStatusCode().is2xxSuccessful()).isTrue();
        Map denialsBody = denialsMetric.getBody();
        assertThat(denialsBody).isNotNull();
        assertThat(denialsBody.get("name")).isEqualTo("access_denials_total");

        // Success metric: increment directly to validate export
        meterRegistry.counter("access_events_success_total").increment();
        ResponseEntity<Map> successMetric = restTemplate.getForEntity(base + "/metrics/access_events_success_total", Map.class);
        assertThat(successMetric.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(successMetric.getBody()).isNotNull();
        assertThat(successMetric.getBody().get("name")).isEqualTo("access_events_success_total");

        // Prometheus scrape endpoint should include our custom counters
        ResponseEntity<String> prom = restTemplate.getForEntity(base + "/prometheus", String.class);
        assertThat(prom.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(prom.getBody()).isNotNull();
        assertThat(prom.getBody()).contains("access_denials_total");
    }
}



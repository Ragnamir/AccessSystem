package com.example.accesssystem.api;

import com.example.accesssystem.api.dto.AccessRuleResponse;
import com.example.accesssystem.api.dto.CreateAccessRuleRequest;
import com.example.accesssystem.api.dto.PageResponse;
import com.example.accesssystem.api.dto.UpdateAccessRuleRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for AccessRule CRUD operations.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class AccessRuleAdminControllerIntegrationTest {

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

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl;
    private UUID userId;
    private UUID zoneBId;
    private UUID zoneCId;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/admin/access-rules";
        // Clean up before each test
        jdbcTemplate.update("DELETE FROM access_rules");
        jdbcTemplate.update("DELETE FROM zones");
        jdbcTemplate.update("DELETE FROM users");
        
        // Create test data
        userId = UUID.randomUUID();
        zoneBId = UUID.randomUUID();
        zoneCId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, code) VALUES (?, ?)", userId, "user-1");
        jdbcTemplate.update("INSERT INTO zones (id, code) VALUES (?, ?)", zoneBId, "zone-b");
        jdbcTemplate.update("INSERT INTO zones (id, code) VALUES (?, ?)", zoneCId, "zone-c");
    }

    @Test
    void createAccessRule_shouldReturnCreatedAccessRule() {
        CreateAccessRuleRequest request = new CreateAccessRuleRequest(userId, zoneBId);

        ResponseEntity<AccessRuleResponse> response = restTemplate.postForEntity(
            baseUrl, request, AccessRuleResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().userId()).isEqualTo(userId);
        assertThat(response.getBody().toZoneId()).isEqualTo(zoneBId);
        assertThat(response.getBody().id()).isNotNull();
        assertThat(response.getBody().createdAt()).isNotNull();
    }

    @Test
    void createAccessRule_withNullToZone_shouldReturnBadRequest() {
        CreateAccessRuleRequest request = new CreateAccessRuleRequest(userId, null);

        ResponseEntity<String> response = restTemplate.postForEntity(
            baseUrl, request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getAccessRule_shouldReturnAccessRule() {
        CreateAccessRuleRequest createRequest = new CreateAccessRuleRequest(userId, zoneBId);
        ResponseEntity<AccessRuleResponse> createResponse = restTemplate.postForEntity(
            baseUrl, createRequest, AccessRuleResponse.class);
        UUID ruleId = createResponse.getBody().id();

        ResponseEntity<AccessRuleResponse> response = restTemplate.getForEntity(
            baseUrl + "/" + ruleId, AccessRuleResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(ruleId);
        assertThat(response.getBody().userId()).isEqualTo(userId);
    }

    @Test
    void listAccessRules_shouldReturnPaginatedResults() {
        restTemplate.postForEntity(baseUrl, 
            new CreateAccessRuleRequest(userId, zoneBId), AccessRuleResponse.class);
        restTemplate.postForEntity(baseUrl, 
            new CreateAccessRuleRequest(userId, zoneCId), AccessRuleResponse.class);

        ResponseEntity<PageResponse<AccessRuleResponse>> response = restTemplate.exchange(
            baseUrl + "?offset=0&limit=10",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<PageResponse<AccessRuleResponse>>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().total()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void listAccessRules_byUserId_shouldReturnFilteredResults() {
        UUID userId2 = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, code) VALUES (?, ?)", userId2, "user-2");
        
        restTemplate.postForEntity(baseUrl, 
            new CreateAccessRuleRequest(userId, zoneBId), AccessRuleResponse.class);
        restTemplate.postForEntity(baseUrl, 
            new CreateAccessRuleRequest(userId2, zoneCId), AccessRuleResponse.class);

        ResponseEntity<PageResponse<AccessRuleResponse>> response = restTemplate.exchange(
            baseUrl + "?userId=" + userId + "&offset=0&limit=10",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<PageResponse<AccessRuleResponse>>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().total()).isEqualTo(1);
    }

    @Test
    void updateAccessRule_shouldUpdateAccessRule() {
        CreateAccessRuleRequest createRequest = new CreateAccessRuleRequest(userId, zoneBId);
        ResponseEntity<AccessRuleResponse> createResponse = restTemplate.postForEntity(
            baseUrl, createRequest, AccessRuleResponse.class);
        UUID ruleId = createResponse.getBody().id();
        UpdateAccessRuleRequest updateRequest = new UpdateAccessRuleRequest(zoneCId);

        restTemplate.put(baseUrl + "/" + ruleId, updateRequest);
        ResponseEntity<AccessRuleResponse> response = restTemplate.getForEntity(
            baseUrl + "/" + ruleId, AccessRuleResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().toZoneId()).isEqualTo(zoneCId);
    }

    @Test
    void deleteAccessRule_shouldDeleteAccessRule() {
        CreateAccessRuleRequest createRequest = new CreateAccessRuleRequest(userId, zoneBId);
        ResponseEntity<AccessRuleResponse> createResponse = restTemplate.postForEntity(
            baseUrl, createRequest, AccessRuleResponse.class);
        UUID ruleId = createResponse.getBody().id();

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
            baseUrl + "/" + ruleId,
            org.springframework.http.HttpMethod.DELETE,
            null,
            Void.class);

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<AccessRuleResponse> getResponse = restTemplate.getForEntity(
            baseUrl + "/" + ruleId, AccessRuleResponse.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}


package com.example.accesssystem.api;

import com.example.accesssystem.api.dto.CreateUserRequest;
import com.example.accesssystem.api.dto.PageResponse;
import com.example.accesssystem.api.dto.UpdateUserRequest;
import com.example.accesssystem.api.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
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
 * Integration tests for User CRUD operations.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class UserAdminControllerIntegrationTest {

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

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/admin/users";
        // Clean up before each test
        jdbcTemplate.update("DELETE FROM access_rules");
        jdbcTemplate.update("DELETE FROM user_state");
        jdbcTemplate.update("DELETE FROM events");
        jdbcTemplate.update("DELETE FROM keys");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void createUser_shouldReturnCreatedUser() {
        CreateUserRequest request = new CreateUserRequest("user-1");

        ResponseEntity<UserResponse> response = restTemplate.postForEntity(
            baseUrl, request, UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("user-1");
        assertThat(response.getBody().id()).isNotNull();
        assertThat(response.getBody().createdAt()).isNotNull();
    }

    @Test
    void createUser_duplicateCode_shouldReturnBadRequest() {
        CreateUserRequest request = new CreateUserRequest("user-1");
        restTemplate.postForEntity(baseUrl, request, UserResponse.class);

        ResponseEntity<String> response = restTemplate.postForEntity(
            baseUrl, request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getUser_shouldReturnUser() {
        CreateUserRequest createRequest = new CreateUserRequest("user-1");
        ResponseEntity<UserResponse> createResponse = restTemplate.postForEntity(
            baseUrl, createRequest, UserResponse.class);
        UUID userId = createResponse.getBody().id();

        ResponseEntity<UserResponse> response = restTemplate.getForEntity(
            baseUrl + "/" + userId, UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("user-1");
        assertThat(response.getBody().id()).isEqualTo(userId);
    }

    @Test
    void getUser_notFound_shouldReturnNotFound() {
        ResponseEntity<UserResponse> response = restTemplate.getForEntity(
            baseUrl + "/" + java.util.UUID.randomUUID(), UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listUsers_shouldReturnPaginatedResults() {
        restTemplate.postForEntity(baseUrl, new CreateUserRequest("user-1"), UserResponse.class);
        restTemplate.postForEntity(baseUrl, new CreateUserRequest("user-2"), UserResponse.class);
        restTemplate.postForEntity(baseUrl, new CreateUserRequest("user-3"), UserResponse.class);

        ResponseEntity<PageResponse> response = restTemplate.getForEntity(
            baseUrl + "?offset=0&limit=2", PageResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().total()).isGreaterThanOrEqualTo(3);
        assertThat(response.getBody().items().size()).isLessThanOrEqualTo(2);
    }

    @Test
    void updateUser_shouldUpdateUser() {
        CreateUserRequest createRequest = new CreateUserRequest("user-1");
        ResponseEntity<UserResponse> createResponse = restTemplate.postForEntity(
            baseUrl, createRequest, UserResponse.class);
        UUID userId = createResponse.getBody().id();
        UpdateUserRequest updateRequest = new UpdateUserRequest("user-1-updated");

        restTemplate.put(baseUrl + "/" + userId, updateRequest);
        ResponseEntity<UserResponse> response = restTemplate.getForEntity(
            baseUrl + "/" + userId, UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("user-1-updated");
    }

    @Test
    void updateUser_notFound_shouldReturnNotFound() {
        UpdateUserRequest updateRequest = new UpdateUserRequest("user-1-updated");

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/" + java.util.UUID.randomUUID(),
            org.springframework.http.HttpMethod.PUT,
            new org.springframework.http.HttpEntity<>(updateRequest),
            String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteUser_shouldDeleteUser() {
        CreateUserRequest createRequest = new CreateUserRequest("user-1");
        ResponseEntity<UserResponse> createResponse = restTemplate.postForEntity(
            baseUrl, createRequest, UserResponse.class);
        UUID userId = createResponse.getBody().id();

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
            baseUrl + "/" + userId,
            org.springframework.http.HttpMethod.DELETE,
            null,
            Void.class);

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<UserResponse> getResponse = restTemplate.getForEntity(
            baseUrl + "/" + userId, UserResponse.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteUser_notFound_shouldReturnNotFound() {
        ResponseEntity<Void> response = restTemplate.exchange(
            baseUrl + "/" + java.util.UUID.randomUUID(),
            org.springframework.http.HttpMethod.DELETE,
            null,
            Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}


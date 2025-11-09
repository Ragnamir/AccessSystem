package com.example.accesssystem.api;

import com.example.accesssystem.domain.contracts.SecurityContracts;
import com.example.accesssystem.service.AntiReplayService;
import com.example.accesssystem.service.CanonicalPayloadBuilder;
import com.example.accesssystem.service.CheckpointKeyRepository;
import com.example.accesssystem.service.DenialRepository;
import com.example.accesssystem.service.IssuerKeyRepository;
import com.example.accesssystem.service.IssuerTokenVerificationService;
import com.example.accesssystem.service.TransactionalEventProcessingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
)
class HealthIntegrationTest {

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	// Mock all beans that require database to avoid dependency on DataSource
	@MockBean
	private CheckpointKeyRepository checkpointKeyRepository;

	@MockBean
	private IssuerKeyRepository issuerKeyRepository;

	@MockBean
	private SecurityContracts.CheckpointMessageVerifier checkpointMessageVerifier;

	@MockBean
	private CanonicalPayloadBuilder canonicalBuilder;

	@MockBean
	private IssuerTokenVerificationService tokenVerificationService;

	@MockBean
	private DenialRepository denialRepository;

	@MockBean
	private AntiReplayService antiReplayService;

	@MockBean
	private TransactionalEventProcessingService transactionalEventProcessingService;

	@MockBean
	private com.example.accesssystem.service.AccessRuleEvaluatorImpl accessRuleEvaluatorImpl;

	@MockBean
	private com.example.accesssystem.service.AccessRuleRepositoryImpl accessRuleRepositoryImpl;

	@MockBean
	private com.example.accesssystem.service.UserStateService userStateService;

	@MockBean
	private com.example.accesssystem.service.UserStateRepositoryImpl userStateRepositoryImpl;

	@MockBean
	private com.example.accesssystem.service.EventRepositoryImpl eventRepositoryImpl;

	@MockBean
	private com.example.accesssystem.service.EventNonceRepositoryImpl eventNonceRepositoryImpl;

	@MockBean
	private com.example.accesssystem.service.CheckpointRepository checkpointRepository;

	@MockBean
	private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

	@MockBean
	private com.example.accesssystem.service.AccessService accessService;

	@Test
	void health_shouldReturnOk() {
		ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
			"http://localhost:" + port + "/health",
			HttpMethod.GET,
			null,
			new ParameterizedTypeReference<>() {}
		);
		assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().get("status")).isEqualTo("OK");
	}
}



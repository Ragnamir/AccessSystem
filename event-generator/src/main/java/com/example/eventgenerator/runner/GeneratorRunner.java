package com.example.eventgenerator.runner;

import com.example.eventgenerator.config.GeneratorProperties;
import com.example.eventgenerator.core.EventPayloadBuilder;
import com.example.eventgenerator.core.Scenario;
import com.example.eventgenerator.infra.SeedService;
import com.example.eventgenerator.infra.UserStateTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import org.springframework.boot.jdbc.DataSourceBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@EnableConfigurationProperties(GeneratorProperties.class)
public class GeneratorRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(GeneratorRunner.class);

    private final GeneratorProperties props;

    public GeneratorRunner(GeneratorProperties props) {
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        DataSource ds = null;
        SeedService.SeededData data;
        if (props.isSeedDatabase()) {
            ds = DataSourceBuilder.create()
                .url(props.getDbUrl())
                .username(props.getDbUser())
                .password(props.getDbPassword())
                .build();
            SeedService seeder = new SeedService(ds);
            data = seeder.seedIfNeeded();
        } else {
            // Without seeding we cannot sign correctly; exit quietly
            return;
        }

        // Create payload builder with default values (will be overridden per event)
        EventPayloadBuilder payloads = new EventPayloadBuilder(
            data.checkpointCode(), data.issuerCode(), data.userCode(),
            data.checkpointKeyPair(), data.issuerKeyPair()
        );

        // Create user state tracker to track user positions
        UserStateTracker stateTracker = new UserStateTracker(ds);

        RestTemplate restTemplate = new RestTemplate();

        // Available users, zones, and checkpoints
        String[] users = SeedService.SeededData.getAvailableUsers();
        String[] zones = SeedService.SeededData.getAvailableZones();
        String[] checkpoints = SeedService.SeededData.getAvailableCheckpoints();

        List<Scenario> rotation = List.of(
            Scenario.VALID_PASSAGE,
            Scenario.BAD_SIGNATURE,
            Scenario.REPLAY_EVENT,
            Scenario.ACCESS_DENIED
        );

        Random random = new Random();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        final int[] idx = {0};
        long periodMs = Math.max(1, 1000L / Math.max(1, props.getRatePerSecond()));
        scheduler.scheduleAtFixedRate(() -> {
            Scenario scenario = rotation.get(idx[0] % rotation.size());
            idx[0]++;
            try {
                String userCode = null;
                String checkpointCode = null;
                String fromZone = null;
                String toZone = null;
                String json = null;
                TransitionCandidate candidate;
                
                switch (scenario) {
                    case VALID_PASSAGE -> {
                        candidate = pickRandomTransition(users, stateTracker, random);
                        if (candidate == null) {
                            logger.info("No available transitions for any user, skipping VALID_PASSAGE event");
                            return;
                        }
                        userCode = candidate.userCode();
                        checkpointCode = candidate.checkpointCode();
                        fromZone = candidate.fromZone();
                        toZone = candidate.toZone();
                        json = payloads.valid(userCode, checkpointCode, fromZone, toZone);
                    }
                    case BAD_SIGNATURE -> {
                        candidate = pickRandomTransition(users, stateTracker, random);
                        if (candidate == null) {
                            logger.info("No available transitions for any user, skipping BAD_SIGNATURE event");
                            return;
                        }
                        userCode = candidate.userCode();
                        checkpointCode = candidate.checkpointCode();
                        fromZone = candidate.fromZone();
                        toZone = candidate.toZone();
                        json = payloads.badSignature(userCode, checkpointCode, fromZone, toZone);
                    }
                    case REPLAY_EVENT -> {
                        candidate = pickRandomTransition(users, stateTracker, random);
                        if (candidate == null) {
                            logger.info("No available transitions for any user, skipping REPLAY_EVENT event");
                            return;
                        }
                        userCode = candidate.userCode();
                        checkpointCode = candidate.checkpointCode();
                        fromZone = candidate.fromZone();
                        toZone = candidate.toZone();
                        String fixed = "00000000-0000-0000-0000-000000000001";
                        json = payloads.replay(userCode, checkpointCode, fromZone, toZone, fixed);
                    }
                    case ACCESS_DENIED -> {
                        // Generate transition that will be denied
                        userCode = users[1]; // user-2
                        checkpointCode = checkpoints[2]; // cp-b-c
                        fromZone = "zone-b";
                        toZone = "zone-c";
                        
                        json = payloads.valid(userCode, checkpointCode, fromZone, toZone);
                    }
                    default -> {
                        candidate = pickRandomTransition(users, stateTracker, random);
                        if (candidate == null) {
                            logger.debug("No available transitions for any user, skipping {}", scenario);
                            return;
                        }
                        userCode = candidate.userCode();
                        checkpointCode = candidate.checkpointCode();
                        fromZone = candidate.fromZone();
                        toZone = candidate.toZone();
                        json = payloads.valid(userCode, checkpointCode, fromZone, toZone);
                    }
                }
                
                if (json == null) {
                    return;
                }
                
                // Логирование генерации события (без ключей)
                String eventInfo = extractEventInfoForLogging(json, scenario, userCode, checkpointCode, fromZone, toZone);
                logger.info("Generated event: {}", eventInfo);
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<String> entity = new HttpEntity<>(json, headers);
                
                ResponseEntity<Map> response = restTemplate.postForEntity(props.getIngestUrl(), entity, Map.class);
                
                // Логирование ответа от системы доступа
                HttpStatusCode statusCode = response.getStatusCode();
                logger.info("Access system response: status={}", statusCode.value());
                
                // Update user state if transition was successful (202 ACCEPTED)
                if (statusCode.value() == 202) {
                    // Refresh state from DB to ensure accuracy (DB was updated by access system)
                    stateTracker.refreshState(userCode);
                    logger.debug("Refreshed state after successful transition: user={}", userCode);
                }
                
            } catch (Exception e) {
                logger.error("Failed to generate or send event: {}", e.getMessage(), e);
            }
        }, 0, periodMs, TimeUnit.MILLISECONDS);

        // Keep app alive; schedule a graceful shutdown hook if needed
    }

    private TransitionCandidate pickRandomTransition(String[] users, UserStateTracker stateTracker, Random random) {
        List<TransitionCandidate> candidates = new ArrayList<>();
        for (String candidateUser : users) {
            Map<String, UserStateTracker.Transition> transitions = stateTracker.getAvailableTransitions(candidateUser);
            for (Map.Entry<String, UserStateTracker.Transition> entry : transitions.entrySet()) {
                UserStateTracker.Transition transition = entry.getValue();
                candidates.add(new TransitionCandidate(candidateUser, entry.getKey(), transition.fromZone(), transition.toZone()));
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    private record TransitionCandidate(String userCode, String checkpointCode, String fromZone, String toZone) {}

    /**
     * Извлекает безопасную информацию о событии для логирования (без ключей, токенов и подписей).
     */
    private String extractEventInfoForLogging(String json, Scenario scenario, String userCode, String checkpointCode, String fromZone, String toZone) {
        // Извлекаем eventId и timestamp из JSON
        String eventId = extractJsonField(json, "eventId");
        String timestamp = extractJsonField(json, "timestamp");
        
        return String.format("scenario=%s, eventId=%s, user=%s, checkpoint=%s, timestamp=%s, fromZone=%s, toZone=%s",
            scenario, eventId, userCode, checkpointCode, timestamp, fromZone, toZone);
    }

    /**
     * Извлекает значение поля из JSON строки.
     */
    private String extractJsonField(String json, String fieldName) {
        String searchPattern = "\"" + fieldName + "\":\"";
        int startIdx = json.indexOf(searchPattern);
        if (startIdx == -1) {
            return "N/A";
        }
        startIdx += searchPattern.length();
        int endIdx = json.indexOf("\"", startIdx);
        if (endIdx == -1) {
            return "N/A";
        }
        return json.substring(startIdx, endIdx);
    }
}



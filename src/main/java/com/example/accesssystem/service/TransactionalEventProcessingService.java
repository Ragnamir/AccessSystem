package com.example.accesssystem.service;

import com.example.accesssystem.domain.DenialReason;
import com.example.accesssystem.domain.Identifiers.UserId;
import com.example.accesssystem.domain.Identifiers.ZoneId;
import com.example.accesssystem.domain.contracts.AccessControlContracts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.example.accesssystem.domain.contracts.AccessControlContracts.AccessDecision.ALLOW;

/**
 * Service for processing events transactionally.
 * Performs the flow: access check -> update user_state -> write events
 * All operations are executed atomically within a single database transaction.
 */
@Service
public class TransactionalEventProcessingService {
    
    private static final Logger log = LoggerFactory.getLogger(TransactionalEventProcessingService.class);
    
    private final AccessControlContracts.AccessRuleEvaluator accessRuleEvaluator;
    private final AccessControlContracts.UserStateService userStateService;
    private final CheckpointRepository checkpointRepository;
    private final EventRepository eventRepository;
    private final DenialRepository denialRepository;
    private final MeterRegistry meterRegistry;
    
    TransactionalEventProcessingService(
        AccessControlContracts.AccessRuleEvaluator accessRuleEvaluator,
        AccessControlContracts.UserStateService userStateService,
        CheckpointRepository checkpointRepository,
        EventRepository eventRepository,
        DenialRepository denialRepository,
        MeterRegistry meterRegistry
    ) {
        this.accessRuleEvaluator = accessRuleEvaluator;
        this.userStateService = userStateService;
        this.checkpointRepository = checkpointRepository;
        this.eventRepository = eventRepository;
        this.denialRepository = denialRepository;
        this.meterRegistry = meterRegistry;
    }
    
    /**
     * Processes an event transactionally: checks access, updates user state, and records the event.
     * All operations are atomic - either all succeed or all fail.
     * 
     * @param eventId the unique event identifier
     * @param checkpointCode the checkpoint code
     * @param userCode the user code
     * @param fromZoneCode the source zone code, can be null for OUT zone
     * @param toZoneCode the destination zone code
     * @param eventTimestamp the timestamp of the event
     * @return ProcessingResult indicating success or failure with reason
     */
    @Transactional
    public ProcessingResult processEvent(
        String eventId,
        String checkpointCode,
        String userCode,
        String fromZoneCode,
        String toZoneCode,
        Instant eventTimestamp
    ) {
        log.debug("Processing event transactionally: eventId={}, checkpoint={}, user={}, from={}, to={}",
            eventId, checkpointCode, userCode, fromZoneCode, toZoneCode);
        
        // Resolve UUIDs from codes
        Optional<UUID> checkpointIdOpt = eventRepository.findCheckpointIdByCode(checkpointCode);
        if (checkpointIdOpt.isEmpty()) {
            log.warn("Checkpoint not found: {}", checkpointCode);
            denialRepository.recordDenial(
                eventId,
                null,
                checkpointCode,
                null,
                userCode,
                null,
                fromZoneCode,
                null,
                toZoneCode,
                DenialReason.INTERNAL_ERROR,
                "Checkpoint not found: " + checkpointCode
            );
            return ProcessingResult.denied("checkpoint_not_found", "Checkpoint not found: " + checkpointCode);
        }
        UUID checkpointId = checkpointIdOpt.get();
        
        Optional<UUID> userIdOpt = eventRepository.findUserIdByCode(userCode);
        if (userIdOpt.isEmpty()) {
            log.warn("User not found: {}", userCode);
            denialRepository.recordDenial(
                eventId,
                checkpointId,
                checkpointCode,
                null,
                userCode,
                null,
                fromZoneCode,
                null,
                toZoneCode,
                DenialReason.INTERNAL_ERROR,
                "User not found: " + userCode
            );
            return ProcessingResult.denied("user_not_found", "User not found: " + userCode);
        }
        UUID userId = userIdOpt.get();
        
        // Handle OUT zone: treat "OUT" as null (outside system)
        String normalizedFromZone = (fromZoneCode == null || fromZoneCode.equals("OUT") || fromZoneCode.isBlank()) 
            ? null : fromZoneCode;
        
        // Resolve zone UUIDs
        Optional<UUID> fromZoneIdOpt = eventRepository.findZoneIdByCode(normalizedFromZone);
        Optional<UUID> toZoneIdOpt = eventRepository.findZoneIdByCode(toZoneCode);
        if (toZoneCode != null && !toZoneCode.equals("OUT") && toZoneIdOpt.isEmpty()) {
            log.warn("To zone not found: {}", toZoneCode);
            UUID fromZoneId = fromZoneIdOpt.orElse(null);
            denialRepository.recordDenial(
                eventId,
                checkpointId,
                checkpointCode,
                userId,
                userCode,
                fromZoneId,
                fromZoneCode,
                null,
                toZoneCode,
                DenialReason.INTERNAL_ERROR,
                "To zone not found: " + toZoneCode
            );
            return ProcessingResult.denied("zone_not_found", "To zone not found: " + toZoneCode);
        }
        
        UUID fromZoneId = fromZoneIdOpt.orElse(null);
        UUID toZoneId = toZoneIdOpt.orElse(null);
        
        // Convert to domain objects (OUT zone is represented as null)
        UserId userIdObj = new UserId(userCode);
        ZoneId fromZoneObj = normalizedFromZone != null ? new ZoneId(normalizedFromZone) : null;
        ZoneId toZoneObj = (toZoneCode != null && !toZoneCode.equals("OUT")) ? new ZoneId(toZoneCode) : null;
        boolean exitAttempt = (toZoneCode == null || "OUT".equals(toZoneCode));
        if (exitAttempt) {
            if (fromZoneId == null || !checkpointRepository.hasExit(fromZoneId)) {
                String message = String.format(
                    "Zone '%s' has no configured exit to OUT",
                    fromZoneCode != null && !fromZoneCode.isBlank() ? fromZoneCode : "UNKNOWN"
                );
                log.info("Exit denied: {}", message);
                denialRepository.recordDenial(
                    eventId,
                    checkpointId,
                    checkpointCode,
                    userId,
                    userCode,
                    fromZoneId,
                    fromZoneCode,
                    null,
                    null,
                    DenialReason.ACCESS_DENIED,
                    message
                );
                return ProcessingResult.denied("no_exit_path", message);
            }
        }
        
        // Check access rules
        AccessControlContracts.AccessDecision decision = 
            accessRuleEvaluator.canTransit(userIdObj, fromZoneObj, toZoneObj);
        
        if (decision != ALLOW) {
            log.info("Access denied for event: eventId={}, user={}, from={}, to={}",
                eventId, userCode, fromZoneCode, toZoneCode);
            denialRepository.recordDenial(
                eventId,
                checkpointId,
                checkpointCode,
                userId,
                userCode,
                fromZoneId,
                fromZoneCode,
                toZoneId,
                toZoneCode,
                DenialReason.ACCESS_DENIED,
                "Access rule not found or denied"
            );
            return ProcessingResult.denied("access_denied", "Access rule not found or denied");
        }
        
        // Validate user's current state matches the from_zone of the transition
        // User cannot transition from a zone they are not currently in
        ZoneId currentZone = userStateService.currentZone(userIdObj);
        
        // Compare current zone with fromZone:
        // - If fromZone is null (entry from outside), currentZone must be null (OUT)
        // - If fromZone is not null (transit between zones or exit to outside), currentZone must match fromZone
        boolean stateMatches;
        if (fromZoneObj == null) {
            // Entry from outside: user must be in OUT zone (currentZone == null)
            stateMatches = (currentZone == null);
        } else {
            // Transit between zones or exit to outside: current zone must match fromZone
            stateMatches = (currentZone != null && currentZone.value().equals(fromZoneObj.value()));
        }
        
        if (!stateMatches) {
            String currentZoneCode = currentZone != null ? currentZone.value() : "OUT";
            String expectedZoneCode = fromZoneObj != null ? fromZoneObj.value() : "OUT";
            String reasonMessage = String.format(
                "User state mismatch: user is currently in zone '%s', but transition requires from zone '%s'",
                currentZoneCode, expectedZoneCode
            );
            log.warn("State mismatch for event: eventId={}, user={}, current={}, expected_from={}, to={}",
                eventId, userCode, currentZoneCode, expectedZoneCode, toZoneCode);
            denialRepository.recordDenial(
                eventId,
                checkpointId,
                checkpointCode,
                userId,
                userCode,
                fromZoneId,
                fromZoneCode,
                toZoneId,
                toZoneCode,
                DenialReason.STATE_MISMATCH,
                reasonMessage
            );
            return ProcessingResult.denied("state_mismatch", reasonMessage);
        }
        
        // Update user state (within transaction)
        try {
            userStateService.updateZone(userIdObj, toZoneObj);
            log.debug("User state updated: user={}, zone={}", userCode, toZoneCode);
        } catch (Exception e) {
            log.error("Failed to update user state for event: eventId={}, user={}", eventId, userCode, e);
            denialRepository.recordDenial(
                eventId,
                checkpointId,
                checkpointCode,
                userId,
                userCode,
                fromZoneId,
                fromZoneCode,
                toZoneId,
                toZoneCode,
                DenialReason.STATE_MISMATCH,
                "Failed to update user state: " + e.getMessage()
            );
            return ProcessingResult.denied("state_update_failed", "Failed to update user state: " + e.getMessage());
        }
        
        // Record event (within transaction)
        try {
            eventRepository.recordEvent(
                eventId,
                checkpointId,
                userId,
                fromZoneId,
                toZoneId,
                eventTimestamp
            );
            log.debug("Event recorded: eventId={}", eventId);
        } catch (Exception e) {
            log.error("Failed to record event: eventId={}", eventId, e);
            denialRepository.recordDenial(
                eventId,
                checkpointId,
                checkpointCode,
                userId,
                userCode,
                fromZoneId,
                fromZoneCode,
                toZoneId,
                toZoneCode,
                DenialReason.INTERNAL_ERROR,
                "Failed to record event: " + e.getMessage()
            );
            return ProcessingResult.denied("event_record_failed", "Failed to record event: " + e.getMessage());
        }
        
        // Metrics: increment success counter
        meterRegistry.counter("access_events_success_total").increment();

        log.info("Event processed successfully: eventId={}, user={}, from={}, to={}",
            eventId, userCode, fromZoneCode, toZoneCode);
        return ProcessingResult.allowed();
    }
    
    /**
     * Result of event processing.
     */
    public static class ProcessingResult {
        private final boolean allowed;
        private final String reason;
        private final String details;
        
        private ProcessingResult(boolean allowed, String reason, String details) {
            this.allowed = allowed;
            this.reason = reason;
            this.details = details;
        }
        
        public static ProcessingResult allowed() {
            return new ProcessingResult(true, "OK", null);
        }
        
        public static ProcessingResult denied(String reason, String details) {
            return new ProcessingResult(false, reason, details);
        }
        
        public boolean isAllowed() {
            return allowed;
        }
        
        public String getReason() {
            return reason;
        }
        
        public String getDetails() {
            return details;
        }
    }
}


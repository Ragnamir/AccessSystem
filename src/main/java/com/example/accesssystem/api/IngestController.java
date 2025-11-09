package com.example.accesssystem.api;

import com.example.accesssystem.domain.DenialReason;
import com.example.accesssystem.domain.Identifiers.CheckpointId;
import com.example.accesssystem.domain.PassageModels.SignedPayload;
import com.example.accesssystem.domain.contracts.SecurityContracts;
import com.example.accesssystem.service.AntiReplayService;
import com.example.accesssystem.service.CanonicalPayloadBuilder;
import com.example.accesssystem.service.DenialRepository;
import com.example.accesssystem.service.IssuerTokenVerificationService;
import com.example.accesssystem.service.TransactionalEventProcessingService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import io.micrometer.core.annotation.Timed;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/ingest")
public class IngestController {

    private static final Logger log = LoggerFactory.getLogger(IngestController.class);
    
    private final SecurityContracts.CheckpointMessageVerifier checkpointMessageVerifier;
    private final CanonicalPayloadBuilder canonicalBuilder;
    private final IssuerTokenVerificationService tokenVerificationService;
    private final AntiReplayService antiReplayService;
    private final TransactionalEventProcessingService transactionalEventProcessingService;
    private final DenialRepository denialRepository;
    
    public IngestController(SecurityContracts.CheckpointMessageVerifier checkpointMessageVerifier,
                           CanonicalPayloadBuilder canonicalBuilder,
                           IssuerTokenVerificationService tokenVerificationService,
                           AntiReplayService antiReplayService,
                           TransactionalEventProcessingService transactionalEventProcessingService,
                           DenialRepository denialRepository) {
        this.checkpointMessageVerifier = checkpointMessageVerifier;
        this.canonicalBuilder = canonicalBuilder;
        this.tokenVerificationService = tokenVerificationService;
        this.antiReplayService = antiReplayService;
        this.transactionalEventProcessingService = transactionalEventProcessingService;
        this.denialRepository = denialRepository;
    }

    @PostMapping("/event")
    @Timed(value = "ingest_event_latency", description = "Ingest endpoint latency")
    public ResponseEntity<Map<String, Object>> ingestEvent(@Valid @RequestBody IngestEventRequest request) {
        log.debug(
            "Ingest event received: checkpointId={}, eventId={}, ts={}, from={}, to={}, token.size={}, sig.size={}",
            request.getCheckpointId(),
            request.getEventId(),
            request.getTimestamp(),
            request.getFromZone(),
            request.getToZone(),
            request.getUserToken() != null ? request.getUserToken().length() : 0,
            request.getSignature() != null ? request.getSignature().length() : 0
        );

        // Anti-replay protection: validate timestamp and check for duplicate eventId
        AntiReplayService.ValidationResult antiReplayResult = antiReplayService.validateEvent(
            request.getEventId(),
            request.getCheckpointId(),
            request.getTimestamp()
        );
        
        if (!antiReplayResult.isAccepted()) {
            log.warn("Anti-replay validation failed for checkpoint {}: {} - {}", 
                request.getCheckpointId(), antiReplayResult.getReason(), antiReplayResult.getDetails());
            
            // Record denial
            denialRepository.recordDenial(
                request.getCheckpointId(),
                DenialReason.REPLAY,
                String.format("Anti-replay validation failed: %s - %s", 
                    antiReplayResult.getReason(), antiReplayResult.getDetails())
            );
            
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "status", "rejected",
                "reason", antiReplayResult.getReason(),
                "checkpointId", request.getCheckpointId(),
                "details", antiReplayResult.getDetails()
            ));
        }

        // Build canonical payload
        byte[] canonicalPayload = canonicalBuilder.buildCanonicalPayload(
            request.getCheckpointId(),
            request.getTimestamp(),
            request.getFromZone(),
            request.getToZone(),
            request.getUserToken()
        );
        
        // Create signed payload: base64(canonical) + "|" + base64(signature)
        String canonicalBase64 = Base64.getEncoder().encodeToString(canonicalPayload);
        String signedPayloadString = canonicalBase64 + "|" + request.getSignature();
        SignedPayload signedPayload = new SignedPayload(signedPayloadString.getBytes(StandardCharsets.UTF_8));
        
        // Verify signature
        CheckpointId checkpointId = new CheckpointId(request.getCheckpointId());
        SecurityContracts.VerificationResult verification = checkpointMessageVerifier.verifyCheckpointMessage(
            checkpointId, signedPayload
        );
        
        if (!verification.valid()) {
            log.warn("Signature verification failed for checkpoint {}: {}", 
                request.getCheckpointId(), verification.reason());
            
            // Record denial
            denialRepository.recordDenial(
                request.getCheckpointId(),
                DenialReason.SIGNATURE_INVALID,
                "Signature verification failed: " + verification.reason()
            );
            
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "status", "rejected",
                "reason", "signature_verification_failed",
                "checkpointId", request.getCheckpointId()
            ));
        }

        // Verify user token (JWT/JWS)
        IssuerTokenVerificationService.TokenVerificationResult tokenResult = 
            tokenVerificationService.verifyAndDecodeToken(request.getUserToken());
        
        if (!tokenResult.isValid()) {
            log.warn("Token verification failed for checkpoint {}: {}", 
                request.getCheckpointId(), tokenResult.getReason());
            
            // Record denial
            denialRepository.recordDenial(
                request.getCheckpointId(),
                DenialReason.TOKEN_INVALID,
                "Token verification failed: " + tokenResult.getReason()
            );
            
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "status", "rejected",
                "reason", "token_verification_failed",
                "checkpointId", request.getCheckpointId(),
                "token_error", tokenResult.getReason()
            ));
        }

        // Extract user ID from token
        String userCode = tokenResult.getUserId()
            .map(u -> u.value())
            .orElseThrow(() -> new IllegalStateException("User ID not found in verified token"));
        
        // Parse timestamp
        Instant eventTimestamp;
        try {
            eventTimestamp = Instant.parse(request.getTimestamp());
        } catch (Exception e) {
            log.warn("Invalid timestamp format: {}", request.getTimestamp());
            
            // Record denial
            denialRepository.recordDenial(
                request.getCheckpointId(),
                DenialReason.INTERNAL_ERROR,
                "Invalid timestamp format: " + request.getTimestamp() + " - " + e.getMessage()
            );
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "status", "rejected",
                "reason", "invalid_timestamp",
                "checkpointId", request.getCheckpointId()
            ));
        }
        
        // Process event transactionally: access check -> update user_state -> write events
        TransactionalEventProcessingService.ProcessingResult processingResult = 
            transactionalEventProcessingService.processEvent(
                request.getEventId(),
                request.getCheckpointId(),
                userCode,
                request.getFromZone(),
                request.getToZone(),
                eventTimestamp
            );
        
        if (!processingResult.isAllowed()) {
            log.warn("Event processing failed: checkpoint={}, eventId={}, reason={}, details={}",
                request.getCheckpointId(), request.getEventId(), 
                processingResult.getReason(), processingResult.getDetails());
            
            // Record denial - denial is already recorded in TransactionalEventProcessingService
            // but we record it here as well with full context in case transaction was rolled back
            try {
                denialRepository.recordDenial(
                    request.getEventId(),
                    null, // checkpointId resolved in service
                    request.getCheckpointId(),
                    null, // userId resolved in service
                    userCode, // userCode already defined above
                    null, // fromZoneId resolved in service
                    request.getFromZone(),
                    null, // toZoneId resolved in service
                    request.getToZone(),
                    mapReasonToDenialReason(processingResult.getReason()),
                    processingResult.getDetails()
                );
            } catch (Exception e) {
                log.error("Failed to record denial", e);
            }
            
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "status", "rejected",
                "reason", processingResult.getReason(),
                "checkpointId", request.getCheckpointId(),
                "details", processingResult.getDetails() != null ? processingResult.getDetails() : ""
            ));
        }

        log.info("Event accepted and processed: checkpoint={}, eventId={}, user={}, from={}, to={}", 
            request.getCheckpointId(), request.getEventId(), userCode, 
            request.getFromZone(), request.getToZone());
        return ResponseEntity.accepted().body(Map.of(
            "status", "accepted",
            "checkpointId", request.getCheckpointId(),
            "eventId", request.getEventId(),
            "userId", userCode
        ));
    }
    
    /**
     * Maps processing result reason to DenialReason enum.
     */
    private DenialReason mapReasonToDenialReason(String reason) {
        if (reason == null) {
            return DenialReason.INTERNAL_ERROR;
        }
        return switch (reason) {
            case "access_denied", "no_exit_path" -> DenialReason.ACCESS_DENIED;
            case "state_update_failed", "state_mismatch" -> DenialReason.STATE_MISMATCH;
            case "checkpoint_not_found", "user_not_found", "zone_not_found", 
                 "event_record_failed" -> DenialReason.INTERNAL_ERROR;
            default -> DenialReason.INTERNAL_ERROR;
        };
    }
}



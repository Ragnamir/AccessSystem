package com.example.accesssystem.service;

import com.example.accesssystem.domain.Identifiers.CheckpointId;
import com.example.accesssystem.domain.PassageModels.SignedPayload;
import com.example.accesssystem.domain.contracts.SecurityContracts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignatureVerificationServiceTest {

    @Mock
    private CheckpointKeyRepository keyRepository;

    @Mock
    private CanonicalPayloadBuilder canonicalBuilder;

    private SignatureVerificationService verificationService;
    private KeyPair rsaKeyPair;
    private String rsaPublicKeyPem;

    @BeforeEach
    void setUp() throws Exception {
        verificationService = new SignatureVerificationService(keyRepository, canonicalBuilder);
        
        // Generate RSA key pair for testing
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        rsaKeyPair = keyGen.generateKeyPair();
        rsaPublicKeyPem = convertToPEM(rsaKeyPair.getPublic(), "PUBLIC KEY");
    }

    @Test
    void verifyCheckpointMessage_validSignature_returnsOk() throws Exception {
        // Given
        CheckpointId checkpointId = new CheckpointId("cp-1");
        String canonicalPayload = "cp-1|2025-01-01T12:00:00Z|zone-a|zone-b|token123";
        byte[] payloadBytes = canonicalPayload.getBytes(StandardCharsets.UTF_8);
        
        // Sign the payload
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(rsaKeyPair.getPrivate());
        signature.update(payloadBytes);
        byte[] signatureBytes = signature.sign();
        String signatureBase64 = Base64.getEncoder().encodeToString(signatureBytes);
        
        // Create signed payload: base64(canonical) + "|" + base64(signature)
        String canonicalBase64 = Base64.getEncoder().encodeToString(payloadBytes);
        String signedPayloadString = canonicalBase64 + "|" + signatureBase64;
        SignedPayload signedPayload = new SignedPayload(signedPayloadString.getBytes(StandardCharsets.UTF_8));
        
        when(keyRepository.findPublicKeyByCheckpointCode("cp-1"))
            .thenReturn(Optional.of(rsaPublicKeyPem));
        when(keyRepository.findKeyTypeByCheckpointCode("cp-1"))
            .thenReturn(Optional.of("RSA"));
        
        // When
        SecurityContracts.VerificationResult result = verificationService.verifyCheckpointMessage(
            checkpointId, signedPayload
        );
        
        // Then
        assertThat(result.valid()).isTrue();
        assertThat(result.reason()).isEqualTo("OK");
    }

    @Test
    void verifyCheckpointMessage_invalidSignature_returnsFailed() throws Exception {
        // Given
        CheckpointId checkpointId = new CheckpointId("cp-1");
        String canonicalPayload = "cp-1|2025-01-01T12:00:00Z|zone-a|zone-b|token123";
        byte[] payloadBytes = canonicalPayload.getBytes(StandardCharsets.UTF_8);
        
        // Create wrong signature (signed with different data)
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(rsaKeyPair.getPrivate());
        signature.update("wrong-data".getBytes(StandardCharsets.UTF_8));
        byte[] signatureBytes = signature.sign();
        String signatureBase64 = Base64.getEncoder().encodeToString(signatureBytes);
        
        // Create signed payload
        String canonicalBase64 = Base64.getEncoder().encodeToString(payloadBytes);
        String signedPayloadString = canonicalBase64 + "|" + signatureBase64;
        SignedPayload signedPayload = new SignedPayload(signedPayloadString.getBytes(StandardCharsets.UTF_8));
        
        when(keyRepository.findPublicKeyByCheckpointCode("cp-1"))
            .thenReturn(Optional.of(rsaPublicKeyPem));
        when(keyRepository.findKeyTypeByCheckpointCode("cp-1"))
            .thenReturn(Optional.of("RSA"));
        
        // When
        SecurityContracts.VerificationResult result = verificationService.verifyCheckpointMessage(
            checkpointId, signedPayload
        );
        
        // Then
        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("Signature verification failed");
    }

    @Test
    void verifyCheckpointMessage_keyNotFound_returnsFailed() {
        // Given
        CheckpointId checkpointId = new CheckpointId("cp-unknown");
        String signedPayloadString = "dGVzdA==|dGVzdA==";
        SignedPayload signedPayload = new SignedPayload(signedPayloadString.getBytes(StandardCharsets.UTF_8));
        
        when(keyRepository.findPublicKeyByCheckpointCode("cp-unknown"))
            .thenReturn(Optional.empty());
        
        // When
        SecurityContracts.VerificationResult result = verificationService.verifyCheckpointMessage(
            checkpointId, signedPayload
        );
        
        // Then
        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("Checkpoint key not found");
    }

    @Test
    void verifyCheckpointMessage_invalidPayloadFormat_returnsFailed() {
        // Given
        CheckpointId checkpointId = new CheckpointId("cp-1");
        // Invalid format: no separator
        String signedPayloadString = "invalid-format";
        SignedPayload signedPayload = new SignedPayload(signedPayloadString.getBytes(StandardCharsets.UTF_8));
        
        when(keyRepository.findPublicKeyByCheckpointCode("cp-1"))
            .thenReturn(Optional.of(rsaPublicKeyPem));
        
        // When
        SecurityContracts.VerificationResult result = verificationService.verifyCheckpointMessage(
            checkpointId, signedPayload
        );
        
        // Then
        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("Invalid payload format");
    }

    @Test
    void verifyCheckpointMessage_corruptedPayload_returnsFailed() {
        // Given
        CheckpointId checkpointId = new CheckpointId("cp-1");
        // Corrupted base64
        String signedPayloadString = "not-valid-base64|dGVzdA==";
        SignedPayload signedPayload = new SignedPayload(signedPayloadString.getBytes(StandardCharsets.UTF_8));
        
        when(keyRepository.findPublicKeyByCheckpointCode("cp-1"))
            .thenReturn(Optional.of(rsaPublicKeyPem));
        when(keyRepository.findKeyTypeByCheckpointCode("cp-1"))
            .thenReturn(Optional.of("RSA"));
        
        // When
        SecurityContracts.VerificationResult result = verificationService.verifyCheckpointMessage(
            checkpointId, signedPayload
        );
        
        // Then
        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("Verification error");
    }

    private String convertToPEM(PublicKey publicKey, String type) {
        // Convert to proper PEM format using X.509 encoding
        byte[] encoded = publicKey.getEncoded();
        String base64 = Base64.getEncoder().encodeToString(encoded);
        StringBuilder pem = new StringBuilder();
        pem.append("-----BEGIN ").append(type).append("-----\n");
        for (int i = 0; i < base64.length(); i += 64) {
            int end = Math.min(i + 64, base64.length());
            pem.append(base64, i, end).append("\n");
        }
        pem.append("-----END ").append(type).append("-----");
        return pem.toString();
    }
}


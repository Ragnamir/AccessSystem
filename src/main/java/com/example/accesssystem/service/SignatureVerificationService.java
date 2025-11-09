package com.example.accesssystem.service;

import com.example.accesssystem.domain.Identifiers.CheckpointId;
import com.example.accesssystem.domain.PassageModels.SignedPayload;
import com.example.accesssystem.domain.contracts.SecurityContracts;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.util.Base64;
import java.util.Optional;

/**
 * Service for verifying checkpoint message signatures.
 */
@Service
public class SignatureVerificationService implements SecurityContracts.CheckpointMessageVerifier {
    
    private static final Logger log = LoggerFactory.getLogger(SignatureVerificationService.class);
    
    private final CheckpointKeyRepository keyRepository;
    private final CanonicalPayloadBuilder canonicalBuilder;
    
    SignatureVerificationService(CheckpointKeyRepository keyRepository, 
                                 CanonicalPayloadBuilder canonicalBuilder) {
        this.keyRepository = keyRepository;
        this.canonicalBuilder = canonicalBuilder;
    }
    
    @Override
    public SecurityContracts.VerificationResult verifyCheckpointMessage(
            CheckpointId checkpointId, SignedPayload payload) {
        
        try {
            // Get public key for checkpoint
            Optional<String> publicKeyPemOpt = keyRepository.findPublicKeyByCheckpointCode(checkpointId.value());
            if (publicKeyPemOpt.isEmpty()) {
                log.warn("Checkpoint public key not found: {}", checkpointId.value());
                return SecurityContracts.VerificationResult.failed("Checkpoint key not found");
            }
            
            Optional<String> keyTypeOpt = keyRepository.findKeyTypeByCheckpointCode(checkpointId.value());
            String keyType = keyTypeOpt.orElse("RSA");
            
            // Parse public key
            PublicKey publicKey = parsePublicKey(publicKeyPemOpt.get(), keyType);
            
            // Extract signature and payload from signed payload
            // Format: base64(canonical_payload) + "|" + base64(signature)
            String payloadString = new String(payload.bytes(), StandardCharsets.UTF_8);
            String[] parts = payloadString.split("\\|", 2);
            if (parts.length != 2) {
                log.warn("Invalid signed payload format for checkpoint: {}", checkpointId.value());
                return SecurityContracts.VerificationResult.failed("Invalid payload format");
            }
            
            String canonicalPayloadBase64 = parts[0];
            String signatureBase64 = parts[1];
            
            byte[] canonicalPayload = Base64.getDecoder().decode(canonicalPayloadBase64);
            byte[] signature = Base64.getDecoder().decode(signatureBase64);
            
            // Verify signature
            boolean valid = verifySignature(publicKey, canonicalPayload, signature, keyType);
            
            if (!valid) {
                log.warn("Signature verification failed for checkpoint: {}", checkpointId.value());
                return SecurityContracts.VerificationResult.failed("Signature verification failed");
            }
            
            log.debug("Signature verification successful for checkpoint: {}", checkpointId.value());
            return SecurityContracts.VerificationResult.ok();
            
        } catch (Exception e) {
            log.error("Error verifying signature for checkpoint: {}", checkpointId.value(), e);
            return SecurityContracts.VerificationResult.failed("Verification error: " + e.getMessage());
        }
    }
    
    private PublicKey parsePublicKey(String publicKeyPem, String keyType) throws IOException, GeneralSecurityException {
        try (PEMParser pemParser = new PEMParser(new StringReader(publicKeyPem))) {
            Object keyObject = pemParser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            
            if (keyObject instanceof SubjectPublicKeyInfo) {
                return converter.getPublicKey((SubjectPublicKeyInfo) keyObject);
            }
            
            // Try to convert using JcaPEMKeyConverter
            try {
                return converter.getPublicKey((org.bouncycastle.asn1.x509.SubjectPublicKeyInfo) keyObject);
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to parse public key. Key type: " + keyType, e);
            }
        }
    }
    
    private boolean verifySignature(PublicKey publicKey, byte[] data, byte[] signature, String keyType) 
            throws GeneralSecurityException {
        
        Signature sig;
        if ("RSA".equalsIgnoreCase(keyType)) {
            sig = Signature.getInstance("SHA256withRSA");
        } else if ("ECDSA".equalsIgnoreCase(keyType)) {
            sig = Signature.getInstance("SHA256withECDSA");
        } else {
            throw new IllegalArgumentException("Unsupported key type for verification: " + keyType);
        }
        
        sig.initVerify(publicKey);
        sig.update(data);
        return sig.verify(signature);
    }
}


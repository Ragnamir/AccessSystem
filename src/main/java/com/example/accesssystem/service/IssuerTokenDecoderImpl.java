package com.example.accesssystem.service;

import com.example.accesssystem.domain.Identifiers.IssuerId;
import com.example.accesssystem.domain.Identifiers.UserId;
import com.example.accesssystem.domain.contracts.SecurityContracts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Real implementation of IssuerTokenDecoder that verifies JWT/JWS tokens.
 */
@Component
public class IssuerTokenDecoderImpl implements SecurityContracts.IssuerTokenDecoder {
    
    private static final Logger log = LoggerFactory.getLogger(IssuerTokenDecoderImpl.class);
    
    private final IssuerTokenVerificationService tokenVerificationService;
    
    IssuerTokenDecoderImpl(IssuerTokenVerificationService tokenVerificationService) {
        this.tokenVerificationService = tokenVerificationService;
    }
    
    @Override
    public Optional<UserId> decodeUserId(IssuerId issuerId, byte[] tokenBytes) {
        if (tokenBytes == null || tokenBytes.length == 0) {
            log.warn("Empty token bytes provided");
            return Optional.empty();
        }
        
        try {
            // Convert token bytes to string (assuming UTF-8 encoding for JWT)
            String tokenString = new String(tokenBytes, StandardCharsets.UTF_8);
            
            // Verify and decode token
            IssuerTokenVerificationService.TokenVerificationResult result = 
                tokenVerificationService.verifyAndDecodeToken(tokenString);
            
            if (!result.isValid()) {
                log.warn("Token verification failed: {}", result.getReason());
                return Optional.empty();
            }
            
            // Verify issuer matches
            Optional<IssuerId> tokenIssuerId = result.getIssuerId();
            if (tokenIssuerId.isEmpty() || !tokenIssuerId.get().equals(issuerId)) {
                log.warn("Token issuer mismatch. Expected: {}, Got: {}", 
                    issuerId, tokenIssuerId.orElse(null));
                return Optional.empty();
            }
            
            // Return user ID from token
            return result.getUserId();
            
        } catch (Exception e) {
            log.error("Error decoding token", e);
            return Optional.empty();
        }
    }
}


package com.example.accesssystem.service;

import com.example.accesssystem.domain.Identifiers.IssuerId;
import com.example.accesssystem.domain.Identifiers.UserId;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Service for verifying issuer tokens (JWT/JWS).
 * Validates token signature, expiration, and extracts user information.
 */
@Service
public class IssuerTokenVerificationService {
    
    private static final Logger log = LoggerFactory.getLogger(IssuerTokenVerificationService.class);
    
    private final IssuerKeyRepository issuerKeyRepository;
    
    IssuerTokenVerificationService(IssuerKeyRepository issuerKeyRepository) {
        this.issuerKeyRepository = issuerKeyRepository;
    }
    
    /**
     * Verifies and decodes a JWT token.
     * 
     * @param tokenString the JWT token as string
     * @return TokenVerificationResult containing validation status and extracted data
     */
    public TokenVerificationResult verifyAndDecodeToken(String tokenString) {
        try {
            // First, parse token without verification to extract issuer ID
            // This allows us to look up the correct public key
            String issuerId;
            String algorithm;
            
            try {
                // Parse token to extract issuer and algorithm without verification
                // Split token into parts
                String[] parts = tokenString.split("\\.");
                if (parts.length < 2) {
                    return TokenVerificationResult.invalid("Invalid token format: missing parts");
                }
                
                // Decode header to get algorithm
                String headerJson = new String(java.util.Base64.getUrlDecoder().decode(parts[0]));
                // Simple JSON parsing for "alg"
                int algStart = headerJson.indexOf("\"alg\"");
                if (algStart >= 0) {
                    int colon = headerJson.indexOf(':', algStart);
                    int quoteStart = headerJson.indexOf('"', colon) + 1;
                    int quoteEnd = headerJson.indexOf('"', quoteStart);
                    algorithm = headerJson.substring(quoteStart, quoteEnd);
                } else {
                    // Default to RS256 if not specified
                    algorithm = "RS256";
                }
                
                // Decode payload to get issuer
                String payloadJson = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
                // Simple JSON parsing for "iss"
                int issStart = payloadJson.indexOf("\"iss\"");
                if (issStart < 0) {
                    return TokenVerificationResult.invalid("Token missing 'iss' claim");
                }
                int colon = payloadJson.indexOf(':', issStart);
                int quoteStart = payloadJson.indexOf('"', colon) + 1;
                int quoteEnd = payloadJson.indexOf('"', quoteStart);
                issuerId = payloadJson.substring(quoteStart, quoteEnd);
                
                if (issuerId == null || issuerId.isBlank()) {
                    return TokenVerificationResult.invalid("Token 'iss' claim is empty");
                }
                
            } catch (Exception e) {
                log.warn("Failed to parse token for issuer extraction: {}", e.getMessage());
                return TokenVerificationResult.invalid("Invalid token format: " + e.getMessage());
            }
            
            // Get public key for issuer
            Optional<String> publicKeyPemOpt = issuerKeyRepository.findPublicKeyByIssuerCode(issuerId);
            if (publicKeyPemOpt.isEmpty()) {
                log.warn("Issuer public key not found: {}", issuerId);
                return TokenVerificationResult.invalid("Issuer key not found: " + issuerId);
            }
            
            // Get expected algorithm from database (fallback to token algorithm)
            Optional<String> dbAlgorithmOpt = issuerKeyRepository.findAlgorithmByIssuerCode(issuerId);
            String expectedAlgorithm = dbAlgorithmOpt.orElse(algorithm);
            
            // Parse public key
            PublicKey publicKey = parsePublicKey(publicKeyPemOpt.get());
            
            // Verify token signature and expiration
            Claims claims;
            try {
                claims = verifyTokenSignature(tokenString, publicKey, expectedAlgorithm);
            } catch (ExpiredJwtException e) {
                log.warn("Token expired: {}", e.getMessage());
                return TokenVerificationResult.expired("Token expired");
            } catch (JwtException e) {
                log.warn("JWT verification failed: {}", e.getMessage());
                return TokenVerificationResult.invalid("JWT verification failed: " + e.getMessage());
            }
            
            // Extract user ID from token
            // Standard claim: "sub" (subject) or custom "userId"
            String userIdValue = claims.get("userId", String.class);
            if (userIdValue == null) {
                userIdValue = claims.getSubject(); // fallback to "sub"
            }
            if (userIdValue == null || userIdValue.isBlank()) {
                return TokenVerificationResult.invalid("Token missing user identifier");
            }
            
            UserId userId = new UserId(userIdValue);
            
            // Extract attributes (all other claims except standard ones)
            Map<String, Object> attributes = claims.entrySet().stream()
                .filter(e -> !isStandardClaim(e.getKey()))
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            
            log.debug("Token verified successfully. Issuer: {}, User: {}", issuerId, userIdValue);
            return TokenVerificationResult.valid(userId, new IssuerId(issuerId), attributes);
            
        } catch (Exception e) {
            log.error("Error verifying token", e);
            return TokenVerificationResult.invalid("Token verification error: " + e.getMessage());
        }
    }
    
    private Claims verifyTokenSignature(String token, PublicKey publicKey, String algorithm) {
        // jjwt 0.12.x uses different API
        var builder = Jwts.parser()
            .verifyWith(publicKey)
            .build();
        
        return builder.parseSignedClaims(token).getPayload();
    }
    
    private PublicKey parsePublicKey(String publicKeyPem) throws IOException {
        try (PEMParser pemParser = new PEMParser(new StringReader(publicKeyPem))) {
            Object keyObject = pemParser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            
            if (keyObject instanceof SubjectPublicKeyInfo) {
                return converter.getPublicKey((SubjectPublicKeyInfo) keyObject);
            }
            
            throw new IllegalArgumentException("Failed to parse public key");
        }
    }
    
    private boolean isStandardClaim(String claimName) {
        return claimName.equals("iss") || claimName.equals("sub") || 
               claimName.equals("aud") || claimName.equals("exp") || 
               claimName.equals("nbf") || claimName.equals("iat") || 
               claimName.equals("jti") || claimName.equals("alg") ||
               claimName.equals("typ");
    }
    
    /**
     * Result of token verification.
     */
    public static class TokenVerificationResult {
        private final boolean valid;
        private final String reason;
        private final UserId userId;
        private final IssuerId issuerId;
        private final Map<String, Object> attributes;
        
        private TokenVerificationResult(boolean valid, String reason, UserId userId, 
                                       IssuerId issuerId, Map<String, Object> attributes) {
            this.valid = valid;
            this.reason = reason;
            this.userId = userId;
            this.issuerId = issuerId;
            this.attributes = attributes;
        }
        
        public static TokenVerificationResult valid(UserId userId, IssuerId issuerId, 
                                                    Map<String, Object> attributes) {
            return new TokenVerificationResult(true, "OK", userId, issuerId, attributes);
        }
        
        public static TokenVerificationResult invalid(String reason) {
            return new TokenVerificationResult(false, reason, null, null, null);
        }
        
        public static TokenVerificationResult expired(String reason) {
            return new TokenVerificationResult(false, reason, null, null, null);
        }
        
        public boolean isValid() { return valid; }
        public String getReason() { return reason; }
        public Optional<UserId> getUserId() { return Optional.ofNullable(userId); }
        public Optional<IssuerId> getIssuerId() { return Optional.ofNullable(issuerId); }
        public Map<String, Object> getAttributes() { 
            return attributes != null ? attributes : java.util.Collections.emptyMap(); 
        }
    }
}


package com.example.accesssystem.service;

import java.util.Optional;

/**
 * Repository for retrieving issuer public keys.
 * Issuers are token-issuing centers that sign user tokens.
 */
public interface IssuerKeyRepository {
    
    /**
     * Retrieves the public key PEM for an issuer by its code.
     * 
     * @param issuerCode the issuer code
     * @return Optional containing the public key PEM if found
     */
    Optional<String> findPublicKeyByIssuerCode(String issuerCode);
    
    /**
     * Retrieves the key type (RSA or ECDSA) for an issuer.
     * 
     * @param issuerCode the issuer code
     * @return Optional containing the key type if found
     */
    Optional<String> findKeyTypeByIssuerCode(String issuerCode);
    
    /**
     * Retrieves the algorithm (RS256, ES256, etc.) for an issuer.
     * 
     * @param issuerCode the issuer code
     * @return Optional containing the algorithm if found
     */
    Optional<String> findAlgorithmByIssuerCode(String issuerCode);
}


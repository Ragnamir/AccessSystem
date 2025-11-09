package com.example.accesssystem.service;

import java.util.Optional;

/**
 * Repository for retrieving checkpoint public keys.
 */
public interface CheckpointKeyRepository {
    
    /**
     * Retrieves the public key PEM for a checkpoint by its code.
     * 
     * @param checkpointCode the checkpoint code
     * @return Optional containing the public key PEM if found
     */
    Optional<String> findPublicKeyByCheckpointCode(String checkpointCode);
    
    /**
     * Retrieves the key type (RSA or ECDSA) for a checkpoint.
     * 
     * @param checkpointCode the checkpoint code
     * @return Optional containing the key type if found
     */
    Optional<String> findKeyTypeByCheckpointCode(String checkpointCode);
}



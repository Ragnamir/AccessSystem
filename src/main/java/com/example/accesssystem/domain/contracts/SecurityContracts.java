package com.example.accesssystem.domain.contracts;

import com.example.accesssystem.domain.Identifiers.CheckpointId;
import com.example.accesssystem.domain.Identifiers.IssuerId;
import com.example.accesssystem.domain.Identifiers.UserId;
import com.example.accesssystem.domain.PassageModels.SignedPayload;

import java.util.Optional;

/**
 * Contracts to separate the what (interfaces) from the how (implementations).
 */
public interface SecurityContracts {

    interface CheckpointMessageVerifier {
        VerificationResult verifyCheckpointMessage(CheckpointId checkpointId, SignedPayload payload);
    }

    interface IssuerTokenDecoder {
        Optional<UserId> decodeUserId(IssuerId issuerId, byte[] tokenBytes);
    }

    record VerificationResult(boolean valid, String reason) {
        public static VerificationResult ok() { return new VerificationResult(true, "OK"); }
        public static VerificationResult failed(String reason) { return new VerificationResult(false, reason); }
    }
}




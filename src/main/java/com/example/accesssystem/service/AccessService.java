package com.example.accesssystem.service;

import com.example.accesssystem.domain.PassageModels.PassageAttempt;
import com.example.accesssystem.domain.contracts.AccessControlContracts;
import com.example.accesssystem.domain.contracts.AccessControlContracts.AccessDecision;
import com.example.accesssystem.domain.contracts.SecurityContracts;
import com.example.accesssystem.domain.contracts.SecurityContracts.VerificationResult;
import org.springframework.stereotype.Service;

import static com.example.accesssystem.domain.contracts.AccessControlContracts.AccessDecision.ALLOW;
import static com.example.accesssystem.domain.contracts.AccessControlContracts.AccessDecision.DENY;

@Service
public class AccessService {

    private final SecurityContracts.CheckpointMessageVerifier checkpointMessageVerifier;
    private final SecurityContracts.IssuerTokenDecoder issuerTokenDecoder;
    private final AccessControlContracts.AccessRuleEvaluator accessRuleEvaluator;
    private final AccessControlContracts.UserStateService userStateService;

    public AccessService(SecurityContracts.CheckpointMessageVerifier checkpointMessageVerifier,
                         SecurityContracts.IssuerTokenDecoder issuerTokenDecoder,
                         AccessControlContracts.AccessRuleEvaluator accessRuleEvaluator,
                         AccessControlContracts.UserStateService userStateService) {
        this.checkpointMessageVerifier = checkpointMessageVerifier;
        this.issuerTokenDecoder = issuerTokenDecoder;
        this.accessRuleEvaluator = accessRuleEvaluator;
        this.userStateService = userStateService;
    }

    public AccessDecision processAttempt(PassageAttempt attempt) {
        VerificationResult verification = checkpointMessageVerifier.verifyCheckpointMessage(
                attempt.checkpointId(), attempt.signedPayload()
        );
        if (!verification.valid()) {
            return DENY;
        }

        // В реальной системе здесь бы использовался decodeUserId(issuerId, tokenBytes)
        // Сейчас принимаем userId как уже известный из попытки (для компиляции и демонстрации контрактов)

        AccessDecision decision = accessRuleEvaluator.canTransit(
                attempt.userId(), attempt.fromZone(), attempt.toZone()
        );

        if (decision == ALLOW) {
            userStateService.updateZone(attempt.userId(), attempt.toZone());
        }

        return decision;
    }
}




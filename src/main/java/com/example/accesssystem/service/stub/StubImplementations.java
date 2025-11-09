package com.example.accesssystem.service.stub;

import com.example.accesssystem.domain.Identifiers.ZoneId;
import com.example.accesssystem.domain.Identifiers.UserId;
import com.example.accesssystem.domain.contracts.AccessControlContracts;
import com.example.accesssystem.domain.contracts.SecurityContracts;
import com.example.accesssystem.domain.PassageModels.SignedPayload;
import com.example.accesssystem.domain.Identifiers.CheckpointId;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Stub implementation - replaced by SignatureVerificationService
// @Component
class StubCheckpointMessageVerifier implements SecurityContracts.CheckpointMessageVerifier {
    @Override
    public SecurityContracts.VerificationResult verifyCheckpointMessage(CheckpointId checkpointId, SignedPayload payload) {
        // Демонстрационная проверка: валидно, если длина байтов > 8
        return (payload.bytes().length > 8)
                ? SecurityContracts.VerificationResult.ok()
                : SecurityContracts.VerificationResult.failed("payload too short");
    }
}

// Stub implementation - replaced by AccessRuleEvaluatorImpl
// @Component
class StubAccessRuleEvaluator implements AccessControlContracts.AccessRuleEvaluator {
    @Override
    public AccessControlContracts.AccessDecision canTransit(UserId userId, ZoneId fromZone, ZoneId toZone) {
        // Демонстрационное правило: разрешаем переход, если зоны различаются
        if (fromZone != null && fromZone.equals(toZone)) {
            return AccessControlContracts.AccessDecision.DENY;
        }
        return AccessControlContracts.AccessDecision.ALLOW;
    }
}

// Stub implementation - replaced by UserStateService with database-backed optimistic locking
// @Component
class InMemoryUserStateService implements AccessControlContracts.UserStateService {
    private final Map<UserId, ZoneId> userZones = new ConcurrentHashMap<>();

    @Override
    public ZoneId currentZone(UserId userId) {
        return userZones.get(userId);
    }

    @Override
    public void updateZone(UserId userId, ZoneId newZone) {
        userZones.put(userId, newZone);
    }
}




package com.example.accesssystem.domain.contracts;

import com.example.accesssystem.domain.Identifiers.ZoneId;
import com.example.accesssystem.domain.Identifiers.UserId;

/**
 * Contracts for evaluating access rules and maintaining user state.
 */
public interface AccessControlContracts {

    interface AccessRuleEvaluator {
        AccessDecision canTransit(UserId userId, ZoneId fromZone, ZoneId toZone);
    }

    interface UserStateService {
        ZoneId currentZone(UserId userId);
        void updateZone(UserId userId, ZoneId newZone);
    }

    enum AccessDecision { ALLOW, DENY }
}




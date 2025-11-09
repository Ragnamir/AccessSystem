package com.example.accesssystem.service;

import com.example.accesssystem.domain.Identifiers.UserId;
import com.example.accesssystem.domain.Identifiers.ZoneId;
import com.example.accesssystem.domain.contracts.AccessControlContracts;
import org.springframework.stereotype.Component;

import static com.example.accesssystem.domain.contracts.AccessControlContracts.AccessDecision.ALLOW;
import static com.example.accesssystem.domain.contracts.AccessControlContracts.AccessDecision.DENY;

/**
 * Implementation of AccessRuleEvaluator that checks access rules from the database.
 * Returns ALLOW if a matching access rule exists, DENY otherwise.
 */
@Component
public class AccessRuleEvaluatorImpl implements AccessControlContracts.AccessRuleEvaluator {
    
    private final AccessRuleRepository accessRuleRepository;
    
    AccessRuleEvaluatorImpl(AccessRuleRepository accessRuleRepository) {
        this.accessRuleRepository = accessRuleRepository;
    }
    
    @Override
    public AccessControlContracts.AccessDecision canTransit(UserId userId, ZoneId fromZone, ZoneId toZone) {
        String userCode = userId.value();
        String toZoneCode = toZone != null ? toZone.value() : null;
        
        boolean hasAccess = accessRuleRepository.hasAccess(userCode, toZoneCode);
        
        return hasAccess ? ALLOW : DENY;
    }
}


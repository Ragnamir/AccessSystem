package com.example.accesssystem.service.stub;

import com.example.accesssystem.domain.Identifiers.IssuerId;
import com.example.accesssystem.domain.Identifiers.UserId;
import com.example.accesssystem.domain.contracts.SecurityContracts;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

// Stub implementation - replaced by IssuerTokenDecoderImpl
// @Component
class StubIssuerTokenDecoder implements SecurityContracts.IssuerTokenDecoder {
    @Override
    public Optional<UserId> decodeUserId(IssuerId issuerId, byte[] tokenBytes) {
        // Простейшая заглушка: трактуем токен как строковый userId
        if (tokenBytes == null || tokenBytes.length == 0) return Optional.empty();
        String raw = new String(tokenBytes, StandardCharsets.UTF_8).trim();
        if (raw.isEmpty()) return Optional.empty();
        return Optional.of(new UserId(raw));
    }
}



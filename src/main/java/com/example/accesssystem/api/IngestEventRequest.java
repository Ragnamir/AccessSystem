package com.example.accesssystem.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Запрос события от пропускного пункта.
 * Минимальная схема на основе Overview:
 * - checkpointId: идентификатор пропускного пункта
 * - timestamp: ISO-8601 строка времени события
 * - fromZone / toZone: направление перехода
 * - userToken: шифротекст идентификатора пользователя
 * - signature: подпись сообщения (подтверждение от пункта)
 * - eventId: уникальный идентификатор события (nonce) для защиты от replay-атак
 */
public class IngestEventRequest {

    @NotBlank
    private String checkpointId;

    @NotBlank
    @Pattern(
        regexp = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?Z$",
        message = "timestamp must be ISO-8601 UTC with 'Z' (e.g. 2025-01-01T12:00:00Z)"
    )
    private String timestamp;

    @NotBlank
    private String fromZone;

    @NotBlank
    private String toZone;

    @NotBlank
    private String userToken;

    @NotBlank
    private String signature;

    @NotBlank
    private String eventId;

    public String getCheckpointId() {
        return checkpointId;
    }

    public void setCheckpointId(String checkpointId) {
        this.checkpointId = checkpointId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getFromZone() {
        return fromZone;
    }

    public void setFromZone(String fromZone) {
        this.fromZone = fromZone;
    }

    public String getToZone() {
        return toZone;
    }

    public void setToZone(String toZone) {
        this.toZone = toZone;
    }

    public String getUserToken() {
        return userToken;
    }

    public void setUserToken(String userToken) {
        this.userToken = userToken;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }
}



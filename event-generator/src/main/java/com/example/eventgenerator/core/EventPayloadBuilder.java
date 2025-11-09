package com.example.eventgenerator.core;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

public class EventPayloadBuilder {
    private final String checkpointCode;
    private final String issuerCode;
    private final String userCode;
    private final KeyPair checkpointKeyPair;
    private final KeyPair issuerKeyPair;

    public EventPayloadBuilder(String checkpointCode,
                               String issuerCode,
                               String userCode,
                               KeyPair checkpointKeyPair,
                               KeyPair issuerKeyPair) {
        this.checkpointCode = checkpointCode;
        this.issuerCode = issuerCode;
        this.userCode = userCode;
        this.checkpointKeyPair = checkpointKeyPair;
        this.issuerKeyPair = issuerKeyPair;
    }

    public String valid(String fromZone, String toZone) {
        return build(userCode, checkpointCode, fromZone, toZone, true, null);
    }

    public String valid(String userCode, String checkpointCode, String fromZone, String toZone) {
        return build(userCode, checkpointCode, fromZone, toZone, true, null);
    }

    public String badSignature(String fromZone, String toZone) {
        return build(userCode, checkpointCode, fromZone, toZone, false, null);
    }

    public String badSignature(String userCode, String checkpointCode, String fromZone, String toZone) {
        return build(userCode, checkpointCode, fromZone, toZone, false, null);
    }

    public String replay(String fromZone, String toZone, String fixedEventId) {
        return build(userCode, checkpointCode, fromZone, toZone, true, fixedEventId);
    }

    public String replay(String userCode, String checkpointCode, String fromZone, String toZone, String fixedEventId) {
        return build(userCode, checkpointCode, fromZone, toZone, true, fixedEventId);
    }

    private String build(String userCode, String checkpointCode, String fromZone, String toZone, boolean signCorrectly, String fixedEventId) {
        // Format timestamp as ISO-8601 UTC with 'Z' suffix (required by API validation)
        // Instant.toString() already produces ISO-8601 format, but we ensure it ends with 'Z'
        Instant now = Instant.now();
        String timestamp = now.toString();
        // Ensure it ends with 'Z' (Instant.toString() should already do this, but be explicit)
        if (!timestamp.endsWith("Z")) {
            timestamp = timestamp.replaceAll("\\+00:00$", "Z");
        }
        String eventId = fixedEventId != null ? fixedEventId : UUID.randomUUID().toString();
        String userToken = Jwts.builder()
            .issuer(issuerCode)
            .subject(userCode)
            .claim("userId", userCode)
            .expiration(Date.from(Instant.now().plusSeconds(3600)))
            .signWith(issuerKeyPair.getPrivate()).compact();

        String canonical = String.join("|", checkpointCode, timestamp, fromZone == null ? "OUT" : fromZone, toZone == null ? "OUT" : toZone, userToken);
        String signatureBase64 = signCorrectly ? sign(canonical) : Base64.getEncoder().encodeToString("bad".getBytes(StandardCharsets.UTF_8));

        // Normalize zones: null -> "OUT"
        String fromZoneNormalized = (fromZone == null || fromZone.equals("OUT")) ? "OUT" : fromZone;
        String toZoneNormalized = (toZone == null || toZone.equals("OUT")) ? "OUT" : toZone;
        
        return "{" +
            quote("checkpointId") + ":" + quote(checkpointCode) + "," +
            quote("eventId") + ":" + quote(eventId) + "," +
            quote("timestamp") + ":" + quote(timestamp) + "," +
            quote("fromZone") + ":" + quote(fromZoneNormalized) + "," +
            quote("toZone") + ":" + quote(toZoneNormalized) + "," +
            quote("userToken") + ":" + quote(userToken) + "," +
            quote("signature") + ":" + quote(signatureBase64) +
            "}";
    }

    private String sign(String canonical) {
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(checkpointKeyPair.getPrivate());
            sig.update(canonical.getBytes(StandardCharsets.UTF_8));
            byte[] bytes = sig.sign();
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign payload", e);
        }
    }

    private static String quote(String s) {
        // Properly escape JSON special characters
        return "\"" + s
            .replace("\\", "\\\\")  // Escape backslashes first
            .replace("\"", "\\\"")  // Escape quotes
            .replace("\n", "\\n")   // Escape newlines
            .replace("\r", "\\r")   // Escape carriage returns
            .replace("\t", "\\t")   // Escape tabs
            + "\"";
    }
}



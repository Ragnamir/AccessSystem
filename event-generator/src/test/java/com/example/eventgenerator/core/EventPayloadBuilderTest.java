package com.example.eventgenerator.core;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

import static org.assertj.core.api.Assertions.assertThat;

class EventPayloadBuilderTest {
    @Test
    void buildsValidJson() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair cp = gen.generateKeyPair();
        KeyPair issuer = gen.generateKeyPair();

        EventPayloadBuilder builder = new EventPayloadBuilder("cp-1", "issuer-1", "user-123", cp, issuer);
        String json = builder.valid("zone-a", "zone-b");
        assertThat(json).contains("\"checkpointId\":\"cp-1\"");
        assertThat(json).contains("\"fromZone\":\"zone-a\"");
        assertThat(json).contains("\"toZone\":\"zone-b\"");
        assertThat(json).contains("\"userToken\":");
        assertThat(json).contains("\"signature\":");
    }
}




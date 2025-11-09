package com.example.eventgenerator.util;

import java.security.PublicKey;
import java.util.Base64;

public final class PemUtil {
    private PemUtil() {}

    public static String toPem(PublicKey key, String type) {
        byte[] encoded = key.getEncoded();
        String base64 = Base64.getEncoder().encodeToString(encoded);
        StringBuilder pem = new StringBuilder();
        pem.append("-----BEGIN ").append(type).append("-----\n");
        for (int i = 0; i < base64.length(); i += 64) {
            int end = Math.min(i + 64, base64.length());
            pem.append(base64, i, end).append("\n");
        }
        pem.append("-----END ").append(type).append("-----");
        return pem.toString();
    }
}




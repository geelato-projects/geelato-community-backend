package cn.geelato.web.common.traffic;

import cn.geelato.core.GlobalContext;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
public class TrafficTagSigner {
    private static final String HMAC_ALGO = "HmacSHA256";

    private final String secret;

    public TrafficTagSigner(String secret) {
        if (secret == null || secret.isBlank()) {
            String generated = generateSecret();
            this.secret = generated;
            if ("product".equalsIgnoreCase(GlobalContext.getEnvironment())) {
                log.error("geelato.traffic.signing-secret is blank, generated an ephemeral secret for current process");
            } else {
                log.warn("geelato.traffic.signing-secret is blank, generated an ephemeral secret for current process");
            }
        } else {
            this.secret = secret;
        }
    }

    public String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean verify(String payload, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        String expected = sign(payload);
        return constantTimeEquals(expected, signature);
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] ba = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        if (ba.length != bb.length) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < ba.length; i++) {
            r |= ba[i] ^ bb[i];
        }
        return r == 0;
    }

    private String generateSecret() {
        byte[] buf = new byte[32];
        new SecureRandom().nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}


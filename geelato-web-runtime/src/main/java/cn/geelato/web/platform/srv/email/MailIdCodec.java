package cn.geelato.web.platform.srv.email;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class MailIdCodec {
    private MailIdCodec() {
    }

    public static String encode(MailKey key) {
        String raw = String.join(":", key.emailAccountId(), key.folder(), String.valueOf(key.uidValidity()), String.valueOf(key.uid()));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static MailKey decode(String mailId) {
        String raw;
        try {
            raw = new String(Base64.getUrlDecoder().decode(mailId), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid mailId");
        }
        String[] parts = raw.split(":", 4);
        if (parts.length != 4) {
            throw new IllegalArgumentException("invalid mailId");
        }
        long uidValidity;
        long uid;
        try {
            uidValidity = Long.parseLong(parts[2]);
            uid = Long.parseLong(parts[3]);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("invalid mailId");
        }
        return new MailKey(parts[0], parts[1], uidValidity, uid);
    }

    public record MailKey(String emailAccountId, String folder, long uidValidity, long uid) {
    }
}


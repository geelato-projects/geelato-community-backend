package cn.geelato.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** decrypt 的密文判定语义 */
class EncryptUtilsTest {

    @Test
    void encryptThenDecryptRoundTrips() {
        String plain = "机密数据-abc-123";
        String cipher = EncryptUtils.encrypt(plain);

        assertEquals(plain, EncryptUtils.decrypt(cipher));
    }

    @Test
    void plainStringWithoutMarkerPassesThrough() {
        assertEquals("hello world", EncryptUtils.decrypt("hello world"));
        assertEquals("中文明文", EncryptUtils.decrypt("中文明文"));
        assertEquals("note: with colon but unknown algo", EncryptUtils.decrypt("note: with colon but unknown algo"));
    }

    @Test
    void timeLikeStringIsNotDamaged() {
        // "12" 命中算法位但非已知算法，走 default 原样返回
        assertEquals("12:30:00", EncryptUtils.decrypt("12:30:00"));
        assertEquals("2026-09-03 12:30:00", EncryptUtils.decrypt("2026-09-03 12:30:00"));
    }

    @Test
    void knownAlgorithmPrefixWithGarbageCipherThrows() {
        // "aes:xxx" 会被当作密文尝试解密，失败抛 IllegalStateException（既有语义）
        assertThrows(IllegalStateException.class, () -> EncryptUtils.decrypt("aes:not-a-valid-cipher"));
    }
}

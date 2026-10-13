package cn.geelato.script.kit;

import cn.geelato.script.core.DefaultScriptEngine;
import cn.geelato.script.core.ScriptEngineBuilder;
import cn.geelato.script.core.ScriptExecution;
import cn.geelato.script.core.ScriptProfile;
import cn.geelato.script.core.ScriptResult;
import cn.geelato.utils.AesUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncryptKitTest {

    private static final String KEY = "builtin-kit-test-key";

    private static DefaultScriptEngine engine;
    private static DefaultScriptEngine noKeyEngine;

    @BeforeAll
    static void init() {
        engine = new ScriptEngineBuilder()
                .encryptKey(KEY)
                .profile(ScriptProfile.builder()
                        .id("default").namespaces(Set.of(ScriptProfile.ALL)).variables(Set.of(ScriptProfile.ALL))
                        .defaultTimeoutMs(5_000).build())
                .build();
        noKeyEngine = new ScriptEngineBuilder().build();
    }

    @AfterAll
    static void shutdown() {
        engine.close();
        noKeyEngine.close();
    }

    private static ScriptResult run(String script) {
        return engine.execute(ScriptExecution.builder().content(script).build());
    }

    @Test
    void md5KnownVector() {
        ScriptResult result = run("return $gl.encrypt.md5('abc')");
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("900150983cd24fb0d6963f7d28e17f72", result.getValue());
    }

    @Test
    void md5ShortIsMiddleSlice() {
        ScriptResult result = run("return $gl.encrypt.md5Short('abc')");
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("3cd24fb0d6963f7d", result.getValue());
    }

    @Test
    void aesRoundTripAndInteropWithAesUtils() {
        ScriptResult encrypted = run("return $gl.encrypt.aes('你好 world')");
        assertEquals("SUCCESS", encrypted.getStatus(), String.valueOf(encrypted.getError()));
        String cipher = String.valueOf(encrypted.getValue());
        assertNotEquals("你好 world", cipher);
        // kit 加密 → 平台 AesUtils 解密（跨实现互操作）
        assertEquals("你好 world", AesUtils.decrypt(cipher, KEY));
        // 平台 AesUtils 加密 → kit 解密
        ScriptResult decrypted = run("return $gl.encrypt.aesDecrypt(" + quote(AesUtils.encrypt("payload-42", KEY)) + ")");
        assertEquals("SUCCESS", decrypted.getStatus(), String.valueOf(decrypted.getError()));
        assertEquals("payload-42", decrypted.getValue());
    }

    @Test
    void aesWithoutKeyThrowsPrecisely() {
        ScriptResult result = noKeyEngine.execute(ScriptExecution.builder()
                .content("return $gl.encrypt.aes('x')").build());
        assertEquals("ERROR", result.getStatus());
        assertTrue(result.getError().getMessage().contains("AES 密钥未配置"), result.getError().getMessage());
    }

    private static String quote(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }
}

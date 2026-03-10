package cn.geelato.mcp.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private static final String TEST_SIGN_KEY = "5A1332068BA9FD17";

    @Test
    void testVerifyWithNullToken() {
        assertThrows(Exception.class, () -> {
            JwtUtil.verify(null, TEST_SIGN_KEY);
        });
    }

    @Test
    void testVerifyWithEmptyToken() {
        assertThrows(Exception.class, () -> {
            JwtUtil.verify("", TEST_SIGN_KEY);
        });
    }

    @Test
    void testVerifyWithInvalidTokenFormat() {
        assertThrows(Exception.class, () -> {
            JwtUtil.verify("invalid-token", TEST_SIGN_KEY);
        });
    }

    @Test
    void testVerifyWithTokenMissingParts() {
        assertThrows(Exception.class, () -> {
            JwtUtil.verify("part1.part2", TEST_SIGN_KEY);
        });
    }

    @Test
    void testVerifyWithTokenTooManyParts() {
        assertThrows(Exception.class, () -> {
            JwtUtil.verify("part1.part2.part3.part4", TEST_SIGN_KEY);
        });
    }

    @Test
    void testVerifyWithMalformedPayload() {
        String malformedToken = "header.invalidPayload.signature";
        assertThrows(Exception.class, () -> {
            JwtUtil.verify(malformedToken, TEST_SIGN_KEY);
        });
    }
}

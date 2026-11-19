package cn.geelato.core.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 系统密级枚举：码值精确映射、3-9 预留区间视同 3、非法码值为 null，能力矩阵与等级定义一致。
 */
class SecurityLevelEnumTest {

    @Test
    void exactCodes() {
        assertEquals(SecurityLevelEnum.DEV, SecurityLevelEnum.fromCode(0));
        assertEquals(SecurityLevelEnum.BASIC, SecurityLevelEnum.fromCode(1));
        assertEquals(SecurityLevelEnum.STANDARD, SecurityLevelEnum.fromCode(2));
        assertEquals(SecurityLevelEnum.ENHANCED, SecurityLevelEnum.fromCode(3));
        assertEquals(SecurityLevelEnum.STRICT, SecurityLevelEnum.fromCode(10));
    }

    @Test
    void reservedBandMapsToEnhanced() {
        for (int code = 4; code <= 9; code++) {
            assertEquals(SecurityLevelEnum.ENHANCED, SecurityLevelEnum.fromCode(code), "code=" + code);
        }
    }

    @Test
    void illegalCodesReturnNull() {
        assertNull(SecurityLevelEnum.fromCode(-1));
        assertNull(SecurityLevelEnum.fromCode(11));
        assertNull(SecurityLevelEnum.fromCode(100));
    }

    @Test
    void capabilityMatrix() {
        assertFalse(SecurityLevelEnum.DEV.hasColumnEncrypt());
        assertFalse(SecurityLevelEnum.DEV.hasApiEncrypt());
        assertTrue(SecurityLevelEnum.DEV.anonymousAllowedByLevel());

        assertTrue(SecurityLevelEnum.BASIC.hasColumnEncrypt());
        assertFalse(SecurityLevelEnum.BASIC.hasApiEncrypt());
        assertTrue(SecurityLevelEnum.BASIC.anonymousAllowedByLevel());

        assertTrue(SecurityLevelEnum.STANDARD.hasColumnEncrypt());
        assertTrue(SecurityLevelEnum.STANDARD.hasApiEncrypt());
        assertTrue(SecurityLevelEnum.STANDARD.anonymousAllowedByLevel());

        assertTrue(SecurityLevelEnum.ENHANCED.hasColumnEncrypt());
        assertTrue(SecurityLevelEnum.ENHANCED.hasApiEncrypt());
        assertTrue(SecurityLevelEnum.ENHANCED.anonymousAllowedByLevel());

        assertTrue(SecurityLevelEnum.STRICT.hasColumnEncrypt());
        assertTrue(SecurityLevelEnum.STRICT.hasApiEncrypt());
        assertFalse(SecurityLevelEnum.STRICT.anonymousAllowedByLevel());
    }
}

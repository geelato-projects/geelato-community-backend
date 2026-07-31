package cn.geelato.web.platform.audit.service;

import cn.geelato.web.platform.audit.boot.AuditLogProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AuditMaskService} 单元测试——验证敏感字段识别与脱敏规则（不依赖框架单例）。
 */
class AuditMaskServiceTest {

    private AuditMaskService service;

    @BeforeEach
    void setUp() {
        AuditLogProperties properties = new AuditLogProperties();
        service = new AuditMaskService(properties);
    }

    @Test
    void isSensitive_matchesConfiguredKeywords() {
        assertTrue(service.isSensitive("password"));
        assertTrue(service.isSensitive("userPwd"));
        assertTrue(service.isSensitive("mobilePhone"));
        assertTrue(service.isSensitive("email"));
        assertTrue(service.isSensitive("idCardNo"));
        assertTrue(service.isSensitive("BANK_CARD"));
        assertFalse(service.isSensitive("orderNo"));
        assertFalse(service.isSensitive("status"));
        assertFalse(service.isSensitive(""));
        assertFalse(service.isSensitive(null));
    }

    @Test
    void mask_password_returnsMask() {
        assertEquals("******", service.mask("password", "abc123!@#"));
    }

    @Test
    void mask_email_keepsDomain() {
        assertEquals("z****@example.com", service.mask("email", "zhang@example.com"));
    }

    @Test
    void mask_mobile_keepsHeadAndTail() {
        assertEquals("138****5678", service.mask("mobile", "13812345678"));
    }

    @Test
    void mask_phone_shortValue() {
        assertEquals("****", service.mask("phone", "1234"));
    }

    @Test
    void mask_idCard() {
        assertEquals("11************1234", service.mask("idCard", "110101199001011234"));
    }

    @Test
    void mask_nonSensitiveField_returnedAsIs() {
        assertEquals("WBL-2024-001", service.mask("orderNo", "WBL-2024-001"));
    }

    @Test
    void mask_nullValue_returnedNull() {
        assertNull(service.mask("password", null));
    }

    @Test
    void mask_nonStringValue_returnedAsIs() {
        Object v = 12345;
        assertEquals(12345, service.mask("amount", v));
    }

    @Test
    void defaultMaskFields_isNotEmpty() {
        AuditLogProperties properties = new AuditLogProperties();
        List<String> fields = properties.getMaskFields();
        assertFalse(fields.isEmpty());
        assertTrue(fields.contains("password"));
        assertTrue(fields.contains("mobile"));
    }
}

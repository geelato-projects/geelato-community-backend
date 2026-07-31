package cn.geelato.web.platform.audit.service;

import cn.geelato.web.platform.audit.boot.AuditLogProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 审计脱敏服务。
 *
 * <p>对审计明细中的敏感字段值打码（不可逆），避免密码/身份证/银行卡/手机号/邮箱等明文进入审计日志。
 * 匹配规则：字段名（忽略大小写）包含配置的 {@code maskFields} 关键词。
 *
 * <p>注：项目现有 {@code EncryptUtils} 是可逆加密，不适用于审计脱敏；本类为审计场景新增的打码工具。
 */
@Component
public class AuditMaskService {

    private final AuditLogProperties properties;

    public AuditMaskService(AuditLogProperties properties) {
        this.properties = properties;
    }

    /** 判断字段名是否为敏感字段。比较时去除下划线/连字符，兼容 bank_card 与 bankCard。 */
    public boolean isSensitive(String fieldName) {
        if (!StringUtils.hasText(fieldName)) {
            return false;
        }
        String normalized = fieldName.toLowerCase().replace("_", "").replace("-", "");
        for (String kw : properties.getMaskFields()) {
            if (kw != null && normalized.contains(kw.toLowerCase().replace("_", "").replace("-", ""))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 对值打码。非敏感字段原样返回；敏感字段按类型打码。
     *
     * @param fieldName 字段名（用于判定是否敏感）
     * @param value     原始值
     * @return 打码后的值；null 原样返回
     */
    public Object mask(String fieldName, Object value) {
        if (value == null) {
            return null;
        }
        if (!isSensitive(fieldName)) {
            return value;
        }
        if (!(value instanceof String s) || s.isEmpty()) {
            return value;
        }
        return maskString(fieldName, s);
    }

    private String maskString(String fieldName, String s) {
        String lower = fieldName.toLowerCase();
        int len = s.length();
        if (lower.contains("password") || lower.contains("pwd")) {
            return "******";
        }
        if (lower.contains("email") && s.indexOf('@') > 0) {
            int at = s.indexOf('@');
            return maskMiddle(s.substring(0, at)) + s.substring(at);
        }
        if (lower.contains("mobile") || lower.contains("phone")) {
            if (len <= 4) {
                return "****";
            }
            return s.substring(0, 3) + repeat(len - 7) + s.substring(len - 4);
        }
        if (lower.contains("idcard") || lower.contains("bankcard")) {
            if (len <= 4) {
                return "****";
            }
            return s.substring(0, 2) + repeat(len - 6) + s.substring(len - 4);
        }
        // 兜底：保留首尾各1，中间打码
        if (len <= 2) {
            return "**";
        }
        return s.charAt(0) + repeat(len - 2) + s.charAt(len - 1);
    }

    private String maskMiddle(String s) {
        if (s.length() <= 1) {
            return "*";
        }
        return s.charAt(0) + repeat(s.length() - 1);
    }

    private String repeat(int n) {
        if (n <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append('*');
        }
        return sb.toString();
    }
}

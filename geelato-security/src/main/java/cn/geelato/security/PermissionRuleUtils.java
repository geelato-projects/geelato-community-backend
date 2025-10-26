package cn.geelato.security;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class PermissionRuleUtils {
    
    // 匹配 #currentUser.fieldName# 或 #currentUser.defaultOrg.fieldName# 格式的变量
    private static final Pattern USER_VARIABLE_PATTERN = Pattern.compile("#currentUser\\.(\\w+(?:\\.\\w+)?)#");
    
    /**
     * 替换权限规则中的用户变量
     * 仅支持获取指定的安全字段，防止敏感信息泄露
     * 
     * 支持的字段（严格驼峰命名）：
     * - 用户基本字段：userId, orgId, defaultOrgId, cooperatingOrgId, deptId, buId, weixinUnionId, weixinWorkUserId, tenantCode
     * - defaultOrg对象字段：defaultOrg.orgId, defaultOrg.deptId, defaultOrg.companyId, defaultOrg.extendId
     * 
     * 使用示例：
     * - #currentUser.userId# -> '123456'
     * - #currentUser.defaultOrg.orgId# -> '789'
     * 
     * @param dp 权限对象
     * @param user 用户对象
     * @return 替换后的规则字符串
     */
    public static String replaceRuleVariable(Permission dp, User user) {
        if (dp == null || dp.getRule() == null || user == null) {
            return dp != null ? dp.getRule() : "";
        }
        
        String rule = dp.getRule();
        Matcher matcher = USER_VARIABLE_PATTERN.matcher(rule);
        
        while (matcher.find()) {
            String fieldName = matcher.group(1); // 获取字段名
            String placeholder = matcher.group(0); // 获取完整的占位符 #currentUser.fieldName#
            
            try {
                Object fieldValue = getFieldValue(user, fieldName);
                String replacement = fieldValue != null ? String.format("'%s'", fieldValue) : "null";
                rule = rule.replace(placeholder, replacement);
                log.debug("替换用户变量: {} -> {}", placeholder, replacement);
            } catch (Exception e) {
                log.warn("获取用户字段值失败: {}, 错误: {}", fieldName, e.getMessage());
            }
        }
        
        return rule;
    }
    
    /**
     * 通过反射获取User对象的字段值
     * 支持驼峰命名和下划线命名的字段
     * 
     * @param user 用户对象
     * @param fieldName 字段名
     * @return 字段值
     * @throws Exception 反射异常
     */
    private static Object getFieldValue(User user, String fieldName) throws Exception {
        // 尝试直接获取getter方法
        String getterName = "get" + capitalize(fieldName);
        
        try {
            Method getter = user.getClass().getMethod(getterName);
            return getter.invoke(user);
        } catch (NoSuchMethodException e) {
            // 如果直接获取失败，尝试一些常见的字段映射
            return getFieldValueWithMapping(user, fieldName);
        }
    }
    
    /**
     * 处理允许的字段映射关系
     * 只允许获取指定的字段，包括defaultOrg对象中的字段
     * 
     * @param user 用户对象
     * @param fieldName 字段名
     * @return 字段值
     * @throws Exception 反射异常或不允许的字段异常
     */
    private static Object getFieldValueWithMapping(User user, String fieldName) throws Exception {
        // 处理允许的字段映射（严格驼峰命名）
        // 不允许的字段，抛出异常
        return switch (fieldName) {
            // 用户基本字段
            case "userId" -> user.getUserId();
            case "orgId" -> user.getOrgId();
            case "defaultOrgId" -> user.getDefaultOrgId();
            case "cooperatingOrgId" -> user.getCooperatingOrgId();
            case "deptId" -> user.getDeptId();
            case "buId" -> user.getBuId();
            case "weixinUnionId" -> user.getWeixinUnionId();
            case "weixinWorkUserId" -> user.getWeixinWorkUserId();
            case "tenantCode" -> user.getTenantCode();

            // defaultOrg对象中的字段
            case "defaultOrg.orgId" -> user.getDefaultOrg() != null ? user.getDefaultOrg().getOrgId() : null;
            case "defaultOrg.deptId" -> user.getDefaultOrg() != null ? user.getDefaultOrg().getDeptId() : null;
            case "defaultOrg.companyId" -> user.getDefaultOrg() != null ? user.getDefaultOrg().getCompanyId() : null;
            case "defaultOrg.extendId" -> user.getDefaultOrg() != null ? user.getDefaultOrg().getExtendId() : null;
            default -> throw new IllegalArgumentException("不允许访问的字段: " + fieldName +
                    "。允许的字段包括: userId, orgId, defaultOrgId, cooperatingOrgId, deptId, buId, " +
                    "weixinUnionId, weixinWorkUserId, tenantCode, defaultOrg.orgId, defaultOrg.deptId, " +
                    "defaultOrg.companyId, defaultOrg.extendId");
        };
    }
    
    /**
     * 首字母大写
     * 
     * @param str 字符串
     * @return 首字母大写的字符串
     */
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}

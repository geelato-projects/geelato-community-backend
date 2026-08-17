package cn.geelato.web.common.interceptor;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 外部系统固定令牌（SystemToken）认证配置。
 * <p>
 * 外部系统调用平台 REST 接口时（如 dyn 模块发送站内信
 * {@code POST /api/notification/send}），不具备传递本系统用户 token 的能力，
 * 以请求头 {@code Authorization: SystemToken <固定密钥>} 认证，
 * 且仅对标注了 {@link cn.geelato.web.common.interceptor.annotation.AllowSystemAccess}
 * 的方法生效。
 * <p>
 * 配置示例（application.properties）：
 * <pre>
 * # 覆盖内置默认密钥（生产环境建议通过环境变量注入随机长串）
 * geelato.security.system-token.token=${GEELATO_SYSTEM_TOKEN}
 * </pre>
 * <p>
 * 机制默认开启，无需开关配置：密钥始终有值（内置默认或配置覆盖）。
 * 后续如需区分多个接入方（各自密钥、各自标识），仅需扩展本类的数据结构与
 * {@link #matches(String)}，拦截器与 Realm 的调用方无需改动。
 */
@Data
@Component
@ConfigurationProperties(prefix = "geelato.security.system-token")
public class SystemTokenProperties {

    /**
     * 内置默认密钥。与前端用户 token 无任何关联，仅用于外部系统调用时的
     * 固定令牌校验；随源码分发，生产环境应覆盖。
     */
    public static final String DEFAULT_TOKEN = "Xk7Tq9Wm@GeElAt0-Sys";

    /**
     * 固定密钥。可用 {@code geelato.security.system-token.token} 或环境变量覆盖。
     */
    private String token = DEFAULT_TOKEN;

    /**
     * 校验请求携带的令牌是否与配置密钥一致（常量时间比较，防时序探测）。
     *
     * @param provided 请求方携带的令牌值（去除前缀后）
     * @return true 表示密钥一致
     */
    public boolean matches(String provided) {
        if (provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}

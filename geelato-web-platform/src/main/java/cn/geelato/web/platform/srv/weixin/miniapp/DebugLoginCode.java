package cn.geelato.web.platform.srv.weixin.miniapp;

import java.util.Locale;
import java.util.Optional;

/**
 * ⚠️⚠️ [DEBUG-REMOVE] 本类整体为联调调试功能，不属于平台正式能力，上线前/稳定后【删除整个文件】。
 * <p>
 * 联调调试凭证解析（受 geelato.wx.miniapp.debug-enabled 开关控制，生产环境必须保持 false——
 * 开启后 code 可绕过微信直接以任意账号身份登录）。
 * <p>
 * 格式：{@code test_{账号}_bind}（模拟"该账号已绑定微信"场景）/
 * {@code test_{账号}_unbind}（模拟"未绑定"场景）。账号段可含下划线（如 cs_zhang_san）。
 * <p>
 * 对应行为：
 * <ul>
 *   <li>login：bind 模式直接以该账号登录（跳过 unionid 反查）；unbind 模式返回未绑定（20011）</li>
 *   <li>bind/unbind 端点：unionid 取确定性假值 {@code debug-union-{账号}}，走正常查重/写库/清绑，
 *       便于反复构造"已绑定/未绑定"数据</li>
 *   <li>WeixinCode 备用通道：bind 模式解析出 {@code debug-union-{账号}}；unbind 模式返回未绑定 401</li>
 * </ul>
 *
 * <p><b>════ [DEBUG-REMOVE] 未来删除清单（全局搜索关键字 [DEBUG-REMOVE] 逐处移除）═════</b>
 * <ol>
 *   <li>本文件 {@code DebugLoginCode.java} 整个删除</li>
 *   <li>{@code WeixinMiniAppLoginService}：login/unbind 的调试分支、{@code debugUnionIdOrResolve()}、
 *       {@code findDebugUserByLoginName()} 两个私有方法，及 bind 中调用点（改回 resolveUnionId）</li>
 *   <li>{@code DefaultWeixinCodeResolver#resolveUnionId} 开头的调试分支</li>
 *   <li>{@code WeixinMiniAppConfigurationProperties#debugEnabled} 字段</li>
 *   <li>{@code META-INF/spring-configuration-metadata.json} 中 geelato.wx.miniapp.debug-enabled 条目</li>
 *   <li>测试：{@code WeixinMiniAppLoginServiceTest} 中 debug* 开头的用例与相关桩</li>
 *   <li>配置示例/文档：application.properties 的 debug-enabled 行、设计文档 §6.7、前端文档注意事项第 9 条</li>
 * </ol>
 */
public final class DebugLoginCode {

    public static final String PREFIX = "test_";
    public static final String BIND_SUFFIX = "_bind";
    public static final String UNBIND_SUFFIX = "_unbind";
    public static final String UNION_ID_PREFIX = "debug-union-";

    public enum Mode { BIND, UNBIND }

    /**
     * [DEBUG-REMOVE] 解析调试手机号凭证：{@code test_{手机号}}（纯数字，如 test_13812341234），
     * 模拟微信 getuserphonenumber 返回该手机号；非 test_ 格式返回 empty（走真实微信解析）。
     */
    public static Optional<String> parsePhone(String phoneCode) {
        if (phoneCode == null) {
            return Optional.empty();
        }
        String lower = phoneCode.toLowerCase(Locale.ROOT);
        if (!lower.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String phone = phoneCode.substring(PREFIX.length());
        // 手机号宽松校验：纯数字、5-20 位（兼容国际号段测试值）
        if (!phone.matches("\\d{5,20}")) {
            return Optional.empty();
        }
        return Optional.of(phone);
    }

    /** 解析结果：账号标识（通常为 loginName）+ 模拟场景。 */
    public record DebugCode(String account, Mode mode) {
    }

    private DebugLoginCode() {
    }

    /**
     * 解析调试凭证；非 test_ 格式返回 empty（走正常微信解析）。
     */
    public static Optional<DebugCode> parse(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String lower = code.toLowerCase(Locale.ROOT);
        if (!lower.startsWith(PREFIX)) {
            return Optional.empty();
        }
        // 账号段可能含下划线（如 cs_zhang_san）：后缀按小写匹配，账号取中间原文（保留原始大小写）
        if (lower.endsWith(BIND_SUFFIX) && code.length() > PREFIX.length() + BIND_SUFFIX.length()) {
            return Optional.of(new DebugCode(
                    code.substring(PREFIX.length(), code.length() - BIND_SUFFIX.length()), Mode.BIND));
        }
        if (lower.endsWith(UNBIND_SUFFIX) && code.length() > PREFIX.length() + UNBIND_SUFFIX.length()) {
            return Optional.of(new DebugCode(
                    code.substring(PREFIX.length(), code.length() - UNBIND_SUFFIX.length()), Mode.UNBIND));
        }
        return Optional.empty();
    }

    /** 调试模式下的确定性假 unionid：同一账号恒定，便于 bind 写入后 login/备用通道反查闭环。 */
    public static String debugUnionId(String account) {
        return UNION_ID_PREFIX + account;
    }
}

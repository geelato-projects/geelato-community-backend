package cn.geelato.web.platform.run;

import cn.geelato.core.GlobalContext;
import cn.geelato.lang.exception.CoreException;
import cn.geelato.lang.exception.ErrorCode;

/**
 * 错误码 → 在线文档 URL 解析器。
 * <p>根据 {@link CoreException} 持有的 {@link ErrorCode} 拼装可定位到在线文档的 URL，
 * 注入到异常响应的 {@code docUrl} 字段，供前端展示"查看文档"入口。</p>
 *
 * <h3>映射规则</h3>
 * <ul>
 *   <li>{@link ErrorCode#getDocSlug()} 非空：{@code {baseUrl}/docs/reference/error-codes/{slug}}（独立详情页）</li>
 *   <li>{@link ErrorCode#getDocSlug()} 为空：{@code {baseUrl}/docs/reference/error-codes#{code}}（汇总页锚点）</li>
 * </ul>
 *
 * <p>当 {@link GlobalContext#getDocUrlEnabled()} 为 false 时返回 null（全局关闭文档跳转）。</p>
 *
 * @author geelato
 */
public class ErrorDocResolver {

    private static final String ERROR_DOCS_PATH = "/docs/reference/error-codes";

    /**
     * 解析异常对应的在线文档 URL。
     *
     * @param ex 平台异常
     * @return 文档 URL；关闭开关或异常为 null 时返回 null
     */
    public String resolve(CoreException ex) {
        if (ex == null || !Boolean.TRUE.equals(GlobalContext.getDocUrlEnabled())) {
            return null;
        }
        ErrorCode ec = ex.getError();
        if (ec == null) {
            return null;
        }
        String base = GlobalContext.getDocBaseUrl();
        String slug = ec.getDocSlug();
        return base + ERROR_DOCS_PATH + (slug != null && !slug.isEmpty()
                ? "/" + slug
                : "#" + ec.getCode());
    }
}

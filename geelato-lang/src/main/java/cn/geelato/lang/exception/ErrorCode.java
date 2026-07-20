package cn.geelato.lang.exception;

/**
 * 错误码元数据的唯一事实源。
 * <p>每个业务错误码以枚举常量形式实现本接口，集中声明其业务码值、默认文案、对应 HTTP 状态码以及在线文档 slug。
 * 异常类通过持有 {@link ErrorCode} 引用而非裸 {@code int} 来获取这些元数据。</p>
 *
 * <h3>字段语义</h3>
 * <ul>
 *   <li>{@link #getCode()} —— int 业务错误码，保持旧码值不变（如 10010、1216），用于前后端契约与文档锚点。</li>
 *   <li>{@link #getDefaultMessage()} —— 默认文案，异常未显式传入文案时使用。</li>
 *   <li>{@link #getHttpStatus()} —— 对应 HTTP 响应状态码，默认 500；鉴权类（401/403/400）应按语义声明，业务码与 HTTP 码分离。</li>
 *   <li>{@link #getDocSlug()} —— 在线文档 slug，默认 null；非空时 docUrl 指向独立详情页，为 null 时指向汇总页锚点。</li>
 * </ul>
 *
 * <h3>docUrl 映射规则</h3>
 * <ul>
 *   <li>{@code getDocSlug() == null}：{@code {baseUrl}/docs/reference/error-codes#{code}}（汇总页锚点）</li>
 *   <li>{@code getDocSlug() != null}：{@code {baseUrl}/docs/reference/error-codes/{slug}}（独立详情页）</li>
 * </ul>
 *
 * @author geelato
 */
public interface ErrorCode {

    /**
     * 业务错误码（int）。
     */
    int getCode();

    /**
     * 默认错误文案。
     */
    String getDefaultMessage();

    /**
     * 对应的 HTTP 响应状态码，默认 500。
     * <p>鉴权类异常应按语义声明为 401/403/400；其余业务异常保持 500。</p>
     */
    default int getHttpStatus() {
        return 500;
    }

    /**
     * 在线文档 slug，默认 null。
     * <p>返回非空值时 docUrl 指向独立详情页 {@code /docs/reference/error-codes/{slug}}；
     * 返回 null 时指向汇总页锚点 {@code /docs/reference/error-codes#{code}}。</p>
     */
    default String getDocSlug() {
        return null;
    }
}

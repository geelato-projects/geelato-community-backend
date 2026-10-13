package cn.geelato.alert.support;

import cn.geelato.lang.api.ApiResult;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 契约语义错误码承载器（业务告警中心）。
 *
 * <p>设计文档以字符串语义码表达失败原因（如 {@code PARAM_INVALID}/{@code STATUS_CONFLICT}）。
 * 为不侵入外部 {@link ApiResult}（其 {@code code} 为数值约定），此处将语义码置于失败响应的
 * {@code data.code}，同时映射一个数值 {@code code} 保持平台惯例，前端以 {@code data.code} 精确判定。
 *
 * <p>关联文档：bea/业务告警中心-接口设计-2026-09-22.md §1
 */
public final class ContractCodes {

    private ContractCodes() {
    }

    public static final String PARAM_INVALID = "PARAM_INVALID";
    public static final String CONTRACT_CHECK_FAILED = "CONTRACT_CHECK_FAILED";
    public static final String STATUS_CONFLICT = "STATUS_CONFLICT";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String NOT_PERMITTED = "NOT_PERMITTED";
    public static final String RULE_DISABLED = "RULE_DISABLED";
    public static final String RENDER_FAIL = "RENDER_FAIL";
    public static final String DUPLICATE_KEY = "DUPLICATE_KEY";
    public static final String VERSION_CONFLICT = "VERSION_CONFLICT";
    public static final String ENGINE_DISABLED = "ENGINE_DISABLED";

    private static int numericOf(String semantic) {
        return switch (semantic) {
            case PARAM_INVALID -> 40000;
            case CONTRACT_CHECK_FAILED -> 42200;
            case STATUS_CONFLICT -> 40900;
            case VERSION_CONFLICT -> 40901;
            case DUPLICATE_KEY -> 40902;
            case RULE_DISABLED -> 40300;
            case NOT_PERMITTED -> 40300;
            case NOT_FOUND -> 40400;
            case RENDER_FAIL -> 40010;
            case ENGINE_DISABLED -> 50100;
            default -> 50000;
        };
    }

    /**
     * 构造携带语义码的失败响应。
     *
     * <p>响应形态：{@code code=数值码, status=失败, msg=message, data={code:语义码, message}}。
     */
    public static ApiResult<Map<String, Object>> fail(String semantic, String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("code", semantic);
        data.put("message", message);
        return ApiResult.fail(data, numericOf(semantic), message);
    }
}

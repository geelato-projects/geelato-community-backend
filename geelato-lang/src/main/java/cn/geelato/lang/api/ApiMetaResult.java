package cn.geelato.lang.api;

import cn.geelato.lang.constants.ApiResultStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * @author geemeta
 *
 */
@Setter
@Getter
public class ApiMetaResult<E> extends ApiResult<E>{

    /**
     * 元数据信息，一般用于实体查询，对查询结果字段的定义信息
     */
    private Object meta;

    public static <T> ApiMetaResult<T> successNoResult() {
        return success(null);
    }

    public static <T> ApiMetaResult<T> success(T data) {
        ApiMetaResult<T> apiResult = new ApiMetaResult<>();
        apiResult.setCode(ResultCode.RC200.getCode());
        apiResult.setStatus(ApiResultStatus.SUCCESS);
        apiResult.setMsg(ResultCode.RC200.getMessage());
        apiResult.setData(data);
        return apiResult;
    }

    public static <T> ApiMetaResult<T> fail(String message) {
        return fail(ResultCode.RC500.getCode(), message);
    }


    private static <T> ApiMetaResult<T> fail(int code, String message) {
        ApiMetaResult<T> apiResult = new ApiMetaResult<>();
        apiResult.setCode(code);
        apiResult.setStatus(ApiResultStatus.FAIL);
        apiResult.setMsg(message);
        return apiResult;
    }


}

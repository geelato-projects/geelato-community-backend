package cn.geelato.lang.api;

import cn.geelato.lang.constants.ApiResultStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * 分页查询的返回结果
 *
 * @author geemeta
 */
@Getter
@Setter
public class ApiPagedResult<E> extends ApiResult<E> {

    private long total;

    private long page;

    private int size;

    private Object meta;

    private int dataSize;

    public static <T> ApiPagedResult<T> success(T data,long page,int size,int dataSize,long total) {
        ApiPagedResult<T> apiPageResult = new ApiPagedResult<>();
        apiPageResult.setCode(ResultCode.RC200.getCode());
        apiPageResult.setStatus(ApiResultStatus.SUCCESS);
        apiPageResult.setMsg(ResultCode.RC200.getMessage());
        apiPageResult.setData(data);
        apiPageResult.setPage(page);
        apiPageResult.setSize(size);
        apiPageResult.setDataSize(dataSize);
        apiPageResult.setTotal(total);
        return apiPageResult;
    }

    public static <T> ApiPagedResult<T> fail(String message) {
        return fail(ResultCode.RC500.getCode(), message);
    }


    private static <T> ApiPagedResult<T> fail(int code, String message) {
        ApiPagedResult<T> apiPagedResult = new ApiPagedResult<>();
        apiPagedResult.setCode(code);
        apiPagedResult.setStatus(ApiResultStatus.FAIL);
        apiPagedResult.setMsg(message);
        return apiPagedResult;
    }

}

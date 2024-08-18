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

    /**
     * 符合查询条件且不分页时的总记录数（相当于dataTotal）
     */
    private long total;

    /**
     *  第几页，从1开始
     */
    private long page;

    /**
     *  每页最多可以展示的记录数，相当于pageSize（当前实体是PagedResult，这个相当于是省列了page字样，留size）
     */
    private int size;

    /**
     * 元数据信息，一般用于实体查询，对查询结果字段的定义信息
     */
    private Object meta;

    /**
     * 本次分页查询的实得记录数，相当于pageDataSize（当前实体是PagedResult，这个相当于是省列了page字样，留dataSize）
     */
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

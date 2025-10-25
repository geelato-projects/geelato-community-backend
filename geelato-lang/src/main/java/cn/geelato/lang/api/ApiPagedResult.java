package cn.geelato.lang.api;

import lombok.Getter;
import lombok.Setter;

/**
 * 分页查询的返回结果
 *
 * @author geemeta
 */
@Getter
@Setter
public class ApiPagedResult<E> extends ApiResult<E>  {

    /**
     * 符合查询条件且不分页时的总记录数（相当于dataTotal）
     */
    private long total;

    /**
     * 第几页，从1开始
     */
    private long page;

    /**
     * 每页最多可以展示的记录数，相当于pageSize（当前实体是PagedResult，这个相当于是省列了page字样，留size）
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

    /**
     * 返回一个操作成功的ApiPagedResult对象，并包含分页信息。
     * <p>该方法创建一个包含成功状态、数据和分页信息的ApiPagedResult对象。
     * 数据部分由参数data提供，分页信息包括当前页码、每页大小、数据总数和总记录数。</p>
     *
     * @param <T>      数据类型
     * @param data     成功返回的数据
     * @param page     当前页码
     * @param size     每页大小
     * @param dataSize 当前页数据条数
     * @param total    总记录数
     * @return 包含成功状态和分页信息的ApiPagedResult对象
     */
    public static <T> ApiPagedResult<T> success(T data, long page, int size, int dataSize, long total) {
        return success(data, page, size, dataSize, total, ResultCode.RC200.getMessage());
    }

    /**
     * 成功返回包含数据和分页信息的ApiPagedResult对象，并允许自定义成功消息。
     * <p>该方法创建一个ApiPagedResult对象，并设置其状态为成功，同时携带指定的数据、分页信息和自定义的成功消息。
     * 数据部分由参数data提供，分页信息包括当前页码、每页大小、当前页数据条数和总记录数。</p>
     *
     * @param <T>      数据类型
     * @param data     成功返回的数据
     * @param page     当前页码
     * @param size     每页大小
     * @param dataSize 当前页数据条数
     * @param total    总记录数
     * @param message  自定义的成功消息
     * @return 返回包含成功状态、数据和分页信息的ApiPagedResult对象
     */
    public static <T> ApiPagedResult<T> success(T data, long page, int size, int dataSize, long total, String message) {
        ApiPagedResult<T> apiPageResult = new ApiPagedResult<>();
        apiPageResult.setCode(ResultCode.RC200.getCode());
        apiPageResult.setStatus(ResultCode.RC200.getStatus());
        apiPageResult.setMsg(message);
        apiPageResult.setData(data);
        apiPageResult.setPage(page);
        apiPageResult.setSize(size);
        apiPageResult.setDataSize(dataSize);
        apiPageResult.setTotal(total);
        return apiPageResult;
    }

    /**
     * 返回一个操作失败的ApiPagedResult对象，并允许自定义失败消息。
     * <p>该方法使用默认的错误代码（ResultCode.RC500.getCode()）和指定的失败消息来创建一个失败的ApiPagedResult对象。</p>
     *
     * @param <T>     数据类型
     * @param message 自定义的失败消息
     * @return 包含操作失败信息和默认错误代码的ApiPagedResult对象
     */
    public static <T> ApiPagedResult<T> fail(String message) {
        return fail(ResultCode.RC500.getCode(), message);
    }

    /**
     * 返回一个操作失败的ApiPagedResult对象，并允许自定义错误代码和失败消息。
     * <p>该方法根据指定的错误代码和失败消息创建一个失败的ApiPagedResult对象。
     * 状态码被设置为ResultCode.RC500.getStatus()，表示操作失败。</p>
     *
     * @param <T>     数据类型
     * @param code    自定义的错误代码
     * @param message 自定义的失败消息
     * @return 包含操作失败信息和自定义错误代码的ApiPagedResult对象
     */
    public static <T> ApiPagedResult<T> fail(int code, String message) {
        ApiPagedResult<T> apiPagedResult = new ApiPagedResult<>();
        apiPagedResult.setCode(code);
        apiPagedResult.setStatus(ResultCode.RC500.getStatus());
        apiPagedResult.setMsg(message);
        return apiPagedResult;
    }
}

package cn.geelato.lang.api;

import lombok.Getter;
import lombok.Setter;

/**
 * @author geemeta
 */
@Setter
@Getter
public class ApiMetaResult<E> extends ApiResult<E> {

    /**
     * 元数据信息，一般用于实体查询，对查询结果字段的定义信息
     */
    private Object meta;

    /**
     * 成功返回没有结果的ApiMetaResult对象
     * <p>该方法创建一个ApiMetaResult对象，并设置其状态为成功，但不包含具体的数据。</p>
     *
     * @param <T> 数据类型
     * @return 返回ApiMetaResult对象
     */
    public static <T> ApiMetaResult<T> successNoResult() {
        return success(null);
    }

    /**
     * 成功返回包含数据的ApiMetaResult对象
     * <p>该方法创建一个ApiMetaResult对象，并设置其状态为成功，同时携带指定的数据。
     * 如果未提供数据，则数据部分将为null。</p>
     *
     * @param <T>  数据类型
     * @param data 成功返回的数据
     * @return 返回ApiMetaResult对象
     */
    public static <T> ApiMetaResult<T> success(T data) {
        return success(data, ResultCode.RC200.getMessage());
    }

    /**
     * 成功返回包含数据的ApiMetaResult对象，并允许自定义成功消息。
     * <p>该方法创建一个ApiMetaResult对象，并设置其状态为成功，同时携带指定的数据和自定义的成功消息。
     * 如果未提供数据，则数据部分将为null。</p>
     *
     * @param <T>     数据类型
     * @param data    成功返回的数据
     * @param message 自定义的成功消息
     * @return 返回包含数据和自定义成功消息的ApiMetaResult对象
     */
    public static <T> ApiMetaResult<T> success(T data, String message) {
        ApiMetaResult<T> apiResult = new ApiMetaResult<>();
        apiResult.setCode(ResultCode.RC200.getCode());
        apiResult.setStatus(ResultCode.RC200.getStatus());
        apiResult.setMsg(message);
        apiResult.setData(data);
        return apiResult;
    }

    /**
     * 返回一个操作失败的ApiMetaResult对象，并允许自定义失败消息。
     * <p>该方法使用默认的错误代码（ResultCode.RC500.getCode()）和指定的失败消息来创建一个失败的ApiMetaResult对象。</p>
     *
     * @param <T>     数据类型
     * @param message 自定义的失败消息
     * @return 返回包含默认错误代码和自定义失败消息的ApiMetaResult对象
     */
    public static <T> ApiMetaResult<T> fail(String message) {
        return fail(ResultCode.RC500.getCode(), message);
    }

    /**
     * 返回一个操作失败的ApiMetaResult对象，并允许自定义失败消息和携带数据。
     * <p>该方法创建一个ApiMetaResult对象，并设置其状态为失败，同时携带指定的数据和自定义的失败消息。
     * 错误代码被设置为ResultCode.RC500.getCode()，表示操作失败。</p>
     *
     * @param <T>     数据类型
     * @param data    操作失败时返回的数据
     * @param message 自定义的失败消息
     * @return 返回包含自定义失败消息和数据的ApiMetaResult对象
     */
    public static <T> ApiMetaResult<T> fail(T data, String message) {
        return fail(data, ResultCode.RC500.getCode(), message);
    }

    /**
     * 返回一个操作失败的ApiMetaResult对象，并允许自定义错误代码和失败消息。
     * <p>该方法创建一个ApiMetaResult对象，并设置其状态为失败，同时允许自定义错误代码和失败消息。
     * 如果未提供数据，则数据部分将为null。</p>
     *
     * @param <T>     数据类型
     * @param code    自定义的错误代码
     * @param message 自定义的失败消息
     * @return 返回包含自定义错误代码和失败消息的ApiMetaResult对象
     */
    public static <T> ApiMetaResult<T> fail(int code, String message) {
        return fail(null, code, message);
    }

    /**
     * 返回一个操作失败的ApiMetaResult对象，并允许自定义错误代码、失败消息和携带数据。
     * <p>该方法创建一个ApiMetaResult对象，并设置其状态为失败，同时允许自定义错误代码、失败消息和携带的数据。
     * 错误状态被设置为ApiResultStatus.FAIL，表示操作失败。</p>
     *
     * @param <T>     数据类型
     * @param data    操作失败时返回的数据
     * @param code    自定义的错误代码
     * @param message 自定义的失败消息
     * @return 返回包含自定义错误代码、失败消息和数据的ApiMetaResult对象
     */
    public static <T> ApiMetaResult<T> fail(T data, int code, String message) {
        ApiMetaResult<T> apiResult = new ApiMetaResult<>();
        apiResult.setCode(code);
        apiResult.setStatus(ResultCode.RC500.getStatus());
        apiResult.setMsg(message);
        apiResult.setData(data);
        return apiResult;
    }

    /**
     * 错误处理函数，用于处理异常。
     * <p>该函数接收一个异常对象作为参数，根据异常类型设置错误代码和错误信息，并返回包含错误信息和状态的
     * ApiResult<E> 对象。</p>
     *
     * @param exception 异常对象，必须是Exception的子类
     * @param <T>       异常类型，必须是Exception的子类
     * @return 返回包含错误信息和状态的 ApiResult<E> 对象
     */
    @Override
    public <T extends Exception> ApiMetaResult<E> exception(T exception) {
        return (ApiMetaResult<E>) super.exception(exception);
    }
}

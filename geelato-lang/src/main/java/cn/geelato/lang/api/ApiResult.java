package cn.geelato.lang.api;

import cn.geelato.lang.exception.CoreException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

/**
 * @author geemeta
 */
@Getter
public class ApiResult<E> {
    private String msg = ResultCode.RC200.getMessage();
    private int code = ResultCode.RC200.getCode();
    @Setter
    private String status = ResultCode.RC200.getStatus();
    private E data;
    private Boolean cache;

    public ApiResult() {

    }

    public ApiResult(E result) {
        setData(result);
    }

    /**
     * 成功返回没有结果的API结果
     *
     * @param <T> 泛型参数，表示API返回结果的数据类型
     * @return 成功且无结果的API结果对象
     */
    public static <T> ApiResult<T> successNoResult() {
        return success(null);
    }

    /**
     * 返回操作成功的ApiResult对象，并包含指定数据。
     *
     * @param <T>  数据类型
     * @param data 操作成功时返回的数据
     * @return 包含操作成功信息的ApiResult对象
     */
    public static <T> ApiResult<T> success(T data) {
        return success(data, ResultCode.RC200.getMessage());
    }

    /**
     * 返回一个操作成功的ApiResult对象，并允许自定义成功消息。
     *
     * @param <T>     数据类型
     * @param data    操作成功时返回的数据
     * @param message 自定义的成功消息
     * @return 包含操作成功信息和数据的ApiResult对象
     */
    public static <T> ApiResult<T> success(T data, String message) {
        ApiResult<T> apiResult = new ApiResult<>();
        apiResult.setCode(ResultCode.RC200.getCode());
        apiResult.setStatus(ResultCode.RC200.getStatus());
        apiResult.setMsg(message);
        apiResult.setData(data);
        return apiResult;
    }

    /**
     * 返回一个操作失败的ApiResult对象，并允许自定义失败消息。
     * <p>该方法使用默认的错误代码（ResultCode.RC500.getCode()）和指定的失败消息来创建一个失败的ApiResult对象。</p>
     *
     * @param <T>     数据类型
     * @param message 自定义的失败消息
     * @return 包含操作失败信息和数据的ApiResult对象
     */
    public static <T> ApiResult<T> fail(String message) {
        return fail(ResultCode.RC500.getCode(), message);
    }

    /**
     * 返回一个操作失败的ApiResult对象，并包含指定的数据。
     * <p>该方法使用默认的错误代码（ResultCode.RC500.getCode()）和默认的失败消息（ResultCode.RC500.getMessage()），
     * 并携带指定的数据来创建一个失败的ApiResult对象。</p>
     *
     * @param <T>  数据类型
     * @param data 操作失败时返回的数据
     * @return 包含操作失败信息和数据的ApiResult对象
     */
    public static <T> ApiResult<T> fail(T data) {
        return fail(data, ResultCode.RC500.getMessage());
    }

    /**
     * 返回一个操作失败的ApiResult对象，并允许自定义失败消息和携带数据。
     * <p>该方法使用默认的错误代码（ResultCode.RC500.getCode()）和指定的失败消息，
     * 携带指定的数据来创建一个失败的ApiResult对象。</p>
     *
     * @param <T>     数据类型
     * @param data    操作失败时返回的数据
     * @param message 自定义的失败消息
     * @return 包含操作失败信息和数据的ApiResult对象
     */
    public static <T> ApiResult<T> fail(T data, String message) {
        return fail(data, ResultCode.RC500.getCode(), message);
    }

    /**
     * 返回一个操作失败的ApiResult对象，并允许自定义错误代码和失败消息。
     * <p>该方法根据指定的错误代码和失败消息创建一个失败的ApiResult对象。
     * 状态码被设置为ResultCode.RC500.getStatus()，表示操作失败。</p>
     *
     * @param <T>     数据类型
     * @param code    自定义的错误代码
     * @param message 自定义的失败消息
     * @return 包含操作失败信息和自定义错误代码的ApiResult对象
     */
    public static <T> ApiResult<T> fail(int code, String message) {
        return fail(null, code, message);
    }

    /**
     * 返回一个操作失败的ApiResult对象，并允许自定义错误代码、失败消息和携带数据。
     * <p>该方法创建一个ApiResult对象，并设置其状态为失败，同时允许自定义错误代码、失败消息和携带的数据。
     * 错误状态码被设置为ResultCode.RC500.getStatus()，表示操作失败。</p>
     *
     * @param <T>     数据类型
     * @param data    操作失败时返回的数据
     * @param code    自定义的错误代码
     * @param message 自定义的失败消息
     * @return 返回包含自定义错误代码、失败消息和数据的ApiResult对象
     */
    public static <T> ApiResult<T> fail(T data, int code, String message) {
        ApiResult<T> apiResult = new ApiResult<>();
        apiResult.setCode(code);
        apiResult.setStatus(ResultCode.RC500.getStatus());
        apiResult.setMsg(message);
        apiResult.setData(data);
        return apiResult;
    }

    public ApiResult<E> setMsg(String msg) {
        this.msg = msg;
        return this;
    }

    public ApiResult<E> setCode(int code) {
        this.code = code;
        return this;
    }

    public ApiResult<E> setData(E data) {
        this.data = data;
        return this;
    }

    /**
     * 设置编码为ApiResultCode.SUCCESS
     * <p>此方法将当前对象的编码、状态和消息分别设置为 ResultCode.RC200 对应的值，并返回当前对象。</p>
     *
     * @return 返回当前对象，类型为 ApiResult<E>
     * @deprecated 该方法已被弃用，请使用新的方法来替代。由于该方法可能在未来版本中移除或更改，
     * 因此建议避免在新代码中使用，并考虑采用推荐的新方法。
     */
    @Deprecated
    public ApiResult<E> success() {
        this.code = ResultCode.RC200.getCode();
        this.status = ResultCode.RC200.getStatus();
        this.msg = ResultCode.RC200.getMessage();
        return this;
    }

    /**
     * 设置编码为ApiResultCode.ERROR，表示操作失败。
     * <p>此方法将当前对象的编码、状态和消息分别设置为 ResultCode.RC500 对应的值，
     * 并返回当前对象，表示操作失败。但请注意，此方法已被弃用，建议使用新的方法来替代。</p>
     *
     * @return 返回ApiResult对象，其中包含操作失败的信息。
     * @deprecated 此方法已被弃用，建议使用新的方法来替代。
     */
    @Deprecated
    public ApiResult<E> error() {
        this.code = ResultCode.RC500.getCode();
        this.status = ResultCode.RC500.getStatus();
        this.msg = ResultCode.RC500.getMessage();
        return this;
    }

    /**
     * 判断操作是否成功
     *
     * @return 如果操作成功，则返回true；否则返回false。
     */
    @JsonIgnore
    public boolean isSuccess() {
        return this.code == ResultCode.RC200.getCode();
    }

    /**
     * 判断操作是否失败
     *
     * @return 如果操作失败，则返回true；否则返回false。
     */
    @JsonIgnore
    public boolean isError() {
        return this.code == ResultCode.RC500.getCode();
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
    public <T extends Exception> ApiResult<E> exception(T exception) {
        this.status = ResultCode.RC500.getStatus();
        if (exception instanceof CoreException) {
            this.code = ((CoreException) exception).getErrorCode();
            this.msg = ((CoreException) exception).getErrorMsg();
        } else {
            this.code = ResultCode.RC500.getCode();
            this.msg = exception.getMessage();
        }
        return this;
    }
}

package cn.geelato.lang.api;

import cn.geelato.lang.constants.ApiResultCode;
import cn.geelato.lang.constants.ApiResultStatus;
import cn.geelato.lang.exception.CoreException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

/**
 * @author geemeta
 */
@Getter
public class ApiResult<E> {
    private String msg;
    private int code = ApiResultCode.SUCCESS;
    @Setter
    private String status = ApiResultStatus.SUCCESS;
    private E data;

    public ApiResult() {

    }

    public ApiResult(E result) {
        setData(result);
    }


    @Deprecated
    public ApiResult<E> setMsg(String msg) {
        this.msg = msg;
        return this;
    }

    @Deprecated
    public ApiResult<E> setCode(int code) {
        this.code = code;
        return this;
    }

    @Deprecated
    public ApiResult<E> setData(E data) {
        this.data = data;
        return this;
    }

    /**
     * 设置编码为ApiResultCode.SUCCESS
     *
     * @return ApiResult 类型的对象
     * @deprecated 该方法已被弃用，请使用新的方法来替代
     */
    @Deprecated
    public ApiResult<E> success() {
        this.code = ApiResultCode.SUCCESS;
        this.status = ApiResultStatus.SUCCESS;
        return this;
    }

    /**
     * 设置编码为ApiResultCode.ERROR，表示操作失败。
     *
     * @return 返回ApiResult对象，其中包含操作失败的信息。
     */
    public ApiResult<E> error() {
        this.code = ApiResultCode.ERROR;
        this.status = ApiResultStatus.FAIL;
        return this;
    }

    /**
     * 错误，异常处理
     *
     * @param exception 异常对象
     * @param <T>       异常类型，必须是Exception的子类
     * @return ApiResult<E> 类型的对象，包含错误信息和状态
     */
    public <T extends Exception> ApiResult<E> error(T exception) {
        this.status = ApiResultStatus.FAIL;
        if (exception instanceof CoreException) {
            this.code = ((CoreException) exception).getErrorCode();
            this.msg = ((CoreException) exception).getErrorMsg();
        } else {
            this.code = ApiResultCode.ERROR;
            this.msg = exception.getMessage();
        }
        return this;
    }


    @JsonIgnore
    public boolean isSuccess() {
        return this.code == ApiResultCode.SUCCESS;
    }

    @JsonIgnore
    public boolean isError() {
        return this.code == ApiResultCode.ERROR;
    }


    public static <T> ApiResult<T> successNoResult() {
        return success(null);
    }

    public static <T> ApiResult<T> success(T data) {
        ApiResult<T> apiResult = new ApiResult<>();
        apiResult.setCode(ResultCode.RC200.getCode());
        apiResult.setStatus(ApiResultStatus.SUCCESS);
        apiResult.setMsg(ResultCode.RC200.getMessage());
        apiResult.setData(data);
        return apiResult;
    }

    public static <T> ApiResult<T> fail(String message) {
        return fail(ResultCode.RC500.getCode(), message);
    }


    private static <T> ApiResult<T> fail(int code, String message) {
        ApiResult<T> apiResult = new ApiResult<>();
        apiResult.setCode(code);
        apiResult.setStatus(ApiResultStatus.FAIL);
        apiResult.setMsg(message);
        return apiResult;
    }
}

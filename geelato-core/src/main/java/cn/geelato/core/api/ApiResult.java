package cn.geelato.core.api;

import cn.geelato.lang.exception.CoreException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import cn.geelato.core.constants.ApiResultCode;
import cn.geelato.core.constants.ApiResultStatus;

/**
 * @author geemeta
 */
public class ApiResult<E> {
    private String msg = "";
    private int code = ApiResultCode.SUCCESS;
    private String status = ApiResultStatus.SUCCESS;
    private E data;

    public ApiResult() {

    }

    public ApiResult(E result) {
        setData(result);
    }

    public ApiResult(E result, String msg, int code) {
        setCode(code);
        setMsg(msg);
        setData(result);
    }

    public String getMsg() {
        return msg;
    }

    public ApiResult<E> setMsg(String msg) {
        this.msg = msg;
        return this;
    }

    public int getCode() {
        return code;
    }

    public ApiResult<E> setCode(int code) {
        this.code = code;
        return this;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public E getData() {
        return data;
    }

    public ApiResult<E> setData(E data) {
        this.data = data;
        return this;
    }
    /**
     * 设置编码为ApiResultCode.SUCCESS
     *
     * @return ApiResult
     */
    public ApiResult<E> success() {
        this.code = ApiResultCode.SUCCESS;
        this.status = ApiResultStatus.SUCCESS;
        return this;
    }
    /**
     * 设置编码为ApiResultCode.ERROR
     *
     * @return ApiResult
     */
    public ApiResult<E> error() {
        this.code = ApiResultCode.ERROR;
        this.status = ApiResultStatus.FAIL;
        return this;
    }

    /**
     * 错误，异常处理
     *
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

}

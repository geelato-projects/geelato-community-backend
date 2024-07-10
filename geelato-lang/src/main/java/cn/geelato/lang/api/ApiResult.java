package cn.geelato.lang.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cn.geelato.lang.constants.ApiResultCode;
import cn.geelato.lang.constants.ApiResultStatus;

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
     * 设置编码为ApiResultCode.SUCCESS
     *
     * @return ApiResult
     */
    public ApiResult<E> success() {
        this.code = ApiResultCode.SUCCESS;
        this.status = ApiResultStatus.SUCCESS;
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

    @JsonIgnore
    public boolean isWarning() {
        return this.code == ApiResultCode.WARNING;
    }

}

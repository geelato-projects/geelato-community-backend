package cn.geelato.mcp.common.model;

public class McpResult<T> {
    private boolean success;
    private T data;
    private String message;
    private String errorCode;

    public static <T> McpResult<T> success(T data) {
        McpResult<T> result = new McpResult<>();
        result.setSuccess(true);
        result.setData(data);
        return result;
    }

    public static <T> McpResult<T> success(T data, String message) {
        McpResult<T> result = success(data);
        result.setMessage(message);
        return result;
    }

    public static <T> McpResult<T> error(String message) {
        McpResult<T> result = new McpResult<>();
        result.setSuccess(false);
        result.setMessage(message);
        return result;
    }

    public static <T> McpResult<T> error(String errorCode, String message) {
        McpResult<T> result = error(message);
        result.setErrorCode(errorCode);
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
}

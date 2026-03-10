package cn.geelato.mcp.common.exception;

public class McpException extends RuntimeException {
    
    private String errorCode;
    
    public McpException(String message) {
        super(message);
    }
    
    public McpException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public McpException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public McpException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}

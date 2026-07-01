package cn.geelato.web.platform.srvlog.model;

import lombok.Data;

@Data
public class SrvLogRecord {
    private String id;
    private long timestamp;
    private String methodKey;
    private String httpMethod;
    private String pathPattern;
    private String handlerSignature;
    private String controllerClass;
    private String methodName;
    private String argsJson;
    private String resultJson;
    private boolean success;
    private int status;
    private Integer apiCode;
    private String apiStatus;
    private String apiMsg;
    private String errorType;
    private long durationMs;
    private String exceptionClass;
    private String exceptionMessage;
    private String stackTrace;
    private String traceId;
}

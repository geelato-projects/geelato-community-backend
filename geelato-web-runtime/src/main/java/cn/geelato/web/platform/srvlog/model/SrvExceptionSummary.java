package cn.geelato.web.platform.srvlog.model;

import lombok.Data;

@Data
public class SrvExceptionSummary {
    private String methodKey;
    private long callCount;
    private long lastCallTime;
    private long exceptionCount;
    private long lastExceptionTime;
    private String handlerSignature;
}


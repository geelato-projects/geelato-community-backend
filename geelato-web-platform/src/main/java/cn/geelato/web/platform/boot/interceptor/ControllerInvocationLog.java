package cn.geelato.web.platform.boot.interceptor;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ControllerInvocationLog {
    private long timestamp;
    private String method;
    private String uri;
    private String url;
    private String handler;
    private Map<String, List<String>> params;
    private Map<String, String> pathParams;
    private String response;
    private long durationMs;
    private int status;
}


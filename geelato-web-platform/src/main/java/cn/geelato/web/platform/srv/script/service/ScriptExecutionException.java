package cn.geelato.web.platform.srv.script.service;

import cn.geelato.meta.Api;
import lombok.Getter;

@Getter
public class ScriptExecutionException extends RuntimeException {
    private final Api api;
    private final boolean retryable;

    public ScriptExecutionException(String message, Throwable cause, Api api, boolean retryable) {
        super(message, cause);
        this.api = api;
        this.retryable = retryable;
    }

}

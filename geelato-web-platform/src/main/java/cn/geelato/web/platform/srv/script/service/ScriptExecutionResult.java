package cn.geelato.web.platform.srv.script.service;

import cn.geelato.meta.Api;

public class ScriptExecutionResult {
    private final Api api;
    private final Object result;
    private final int attemptCount;

    public ScriptExecutionResult(Api api, Object result, int attemptCount) {
        this.api = api;
        this.result = result;
        this.attemptCount = attemptCount;
    }

    public Api getApi() {
        return api;
    }

    public Object getResult() {
        return result;
    }

    public int getAttemptCount() {
        return attemptCount;
    }
}

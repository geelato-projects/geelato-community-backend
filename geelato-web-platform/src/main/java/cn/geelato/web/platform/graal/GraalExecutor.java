package cn.geelato.web.platform.graal;

import cn.geelato.web.platform.srv.script.service.ScriptExecutionService;

public class GraalExecutor {
    private final ScriptExecutionService scriptExecutionService;

    public GraalExecutor(ScriptExecutionService scriptExecutionService) {
        this.scriptExecutionService = scriptExecutionService;
    }

    public Object exec(String code, Object parameter) {
        return scriptExecutionService.executeByCode(code, parameter);
    }
}

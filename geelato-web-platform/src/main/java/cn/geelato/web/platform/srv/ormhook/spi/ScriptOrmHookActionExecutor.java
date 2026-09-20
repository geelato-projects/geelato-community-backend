package cn.geelato.web.platform.srv.ormhook.spi;

import cn.geelato.meta.Api;
import cn.geelato.meta.OrmHook;
import cn.geelato.web.platform.srv.script.service.ScriptExecutionResult;
import cn.geelato.web.platform.srv.script.service.ScriptExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * script 动作执行器：执行内嵌于钩子配置的 JavaScript 脚本。
 * <p>
 * 脚本内容存于 {@code platform_orm_hook.script_content}（不依赖 platform_api 资产），
 * 通过构造临时 {@link Api}（仅填 releaseContent/code，不落库）复用
 * {@link ScriptExecutionService#executeApi} 的完整 GraalJS 执行设施——
 * 脚本内可直接使用 http/dao/config/user 等服务。
 * 脚本契约（与平台脚本一致）：编写函数体 {@code function(){ ... return 结果; }}，
 * 触发载荷经函数形参闭包的 {@code parameter}（或 {@code $gl.ctx.parameter}）获取。
 *
 * @author geelato
 */
@Component
@Slf4j
public class ScriptOrmHookActionExecutor implements OrmHookActionExecutor {

    private final ScriptExecutionService scriptExecutionService;

    public ScriptOrmHookActionExecutor(ScriptExecutionService scriptExecutionService) {
        this.scriptExecutionService = scriptExecutionService;
    }

    @Override
    public String getType() {
        return TYPE_SCRIPT;
    }

    @Override
    public OrmHookActionResult execute(OrmHook rule, Map<String, Object> payload) {
        try {
            Api transientApi = new Api();
            transientApi.setCode("orm-hook-" + rule.getId());
            transientApi.setReleaseContent(rule.getScriptContent());
            ScriptExecutionResult result = scriptExecutionService.executeApi(transientApi, payload, 0);
            if (log.isDebugEnabled()) {
                log.debug("ORM Hook script 动作执行完成 hookId={}, attempts={}, result={}",
                        rule.getId(), result.getAttemptCount(), result.getResult());
            }
            return OrmHookActionResult.success();
        } catch (Exception e) {
            log.warn("ORM Hook script 动作执行失败 hookId={}: {}", rule.getId(), e.getMessage());
            return OrmHookActionResult.fail(e.getMessage());
        }
    }
}

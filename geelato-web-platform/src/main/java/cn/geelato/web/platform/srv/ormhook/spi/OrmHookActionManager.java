package cn.geelato.web.platform.srv.ormhook.spi;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 钩子动作执行器注册表：收集全部 {@link OrmHookActionExecutor} Bean，按动作类型路由
 * （对齐通知中心 {@code DeliveryChannelManager} 模式）。
 *
 * @author geelato
 */
@Component
public class OrmHookActionManager {

    private final Map<String, OrmHookActionExecutor> executors = new HashMap<>();

    public OrmHookActionManager(List<OrmHookActionExecutor> executorList) {
        if (executorList != null) {
            for (OrmHookActionExecutor executor : executorList) {
                OrmHookActionExecutor existed = executors.put(executor.getType(), executor);
                if (existed != null) {
                    throw new IllegalStateException(String.format(
                            "钩子动作类型 %s 存在重复实现：%s 与 %s",
                            executor.getType(), existed.getClass().getName(), executor.getClass().getName()));
                }
            }
        }
    }

    /** 按动作类型取执行器，无实现或类型为空返回 null（调用方转死信）。 */
    public OrmHookActionExecutor getExecutor(String actionType) {
        return actionType == null || actionType.isBlank() ? null : executors.get(actionType);
    }

    /** 动作类型是否已注册。 */
    public boolean supports(String actionType) {
        return getExecutor(actionType) != null;
    }
}

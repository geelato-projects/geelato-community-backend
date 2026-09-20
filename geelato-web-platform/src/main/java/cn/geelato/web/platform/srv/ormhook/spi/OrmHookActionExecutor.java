package cn.geelato.web.platform.srv.ormhook.spi;

import cn.geelato.meta.OrmHook;

import java.util.Map;

/**
 * 钩子动作执行器 SPI（可插拔动作类型）。
 * <p>
 * 钩子机制 = 事件匹配 + 投递编排；每个动作类型实现本接口，由发件箱处理器在执行阶段按
 * {@code actionType} 路由调用（对齐通知中心 {@code DeliveryChannel} 模式）。
 * <ul>
 *   <li>{@link ScriptOrmHookActionExecutor}（script）：执行内嵌于钩子配置的 JavaScript 脚本</li>
 *   <li>{@link HttpOrmHookActionExecutor}（http）：按钩子配置直接调用 HTTP 接口</li>
 *   <li>未来新增动作只需实现本接口并注册为 Spring Bean</li>
 * </ul>
 * 实现约定：<b>同步执行、可抛异常</b>（异常由调用方统一转 fail 处理）；
 * 返回 fail 只会进重试/死信，<b>绝不影响业务链路</b>。执行入参为<b>当前</b>钩子配置
 * （发件箱按 hook_id 回读），实现方无需自行处理配置加载。
 *
 * @author geelato
 */
public interface OrmHookActionExecutor {

    /** 动作类型：执行内嵌脚本（script_content）。 */
    String TYPE_SCRIPT = "script";

    /** 动作类型：调用 HTTP 接口（http_url 等）。 */
    String TYPE_HTTP = "http";

    /**
     * 动作类型标识，与 {@code platform_orm_hook.action_type} 对应。
     */
    String getType();

    /**
     * 执行动作。
     *
     * @param rule    当前钩子配置（含动作所需的全部字段）
     * @param payload 触发载荷（values 新值快照、session 会话信息、eventId 等）
     * @return 执行结果；fail 进入重试/死信
     */
    OrmHookActionResult execute(OrmHook rule, Map<String, Object> payload);
}

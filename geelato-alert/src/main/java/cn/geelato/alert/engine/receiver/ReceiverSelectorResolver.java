package cn.geelato.alert.engine.receiver;

import java.util.List;
import java.util.Map;

/** 接收人选择器 SPI：按 type 分发（USER/ROLE/DEPT/DEPT_ROLE/BIZ_FIELD/ORG_TREE_UP），与异常中心 owner_selector 同一套语义。 */
public interface ReceiverSelectorResolver {

    String type();

    /**
     * @param selector 选择器节点（{type, userId?/userIds?, roleCode?, orgId?/org:{scope}, level?...}）
     * @param ctx      命中源实例上下文
     * @return 求解结果（空列表=该选择器无人）
     */
    List<AlertReceiver> resolve(Map<String, Object> selector, AlertResolveCtx ctx);
}

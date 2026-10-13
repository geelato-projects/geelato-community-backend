package cn.geelato.exception.engine.participant;

import java.util.List;
import java.util.Map;

/**
 * 参与者求解器 SPI：按选择器 type 分发（USER/ROLE/DEPT/DEPT_ROLE/BIZ_FIELD/ORG_TREE_UP...）。
 */
public interface ParticipantResolver {

    String type();

    /**
     * @param selector 选择器节点（{type, userId?, roleCode?, orgId?/org:{scope}, ...}）
     * @param hit      命中对象（bizType/bizId/customerId/ownerId/ownerOrgId/snapshot...）
     * @return 求解结果（空列表=该选择器无人，调用方跳过）
     */
    List<Participant> resolve(Map<String, Object> selector, Map<String, Object> hit);
}

package cn.geelato.alert.engine.receiver;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 接收人求解上下文：命中源实例（EXCEPTION 源）的业务责任人要素，BIZ_FIELD / org.scope=BIZ_ORG 选择器取值来源。 */
@Getter
@AllArgsConstructor
public class AlertResolveCtx {

    private final String ownerId;
    private final String ownerName;
    private final String ownerOrgId;

    public static AlertResolveCtx empty() {
        return new AlertResolveCtx(null, null, null);
    }
}

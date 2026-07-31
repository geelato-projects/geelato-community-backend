package cn.geelato.web.platform.audit.annotation;

import cn.geelato.web.platform.audit.enums.AuditOperType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式业务动作审计注解（第1层）。
 *
 * <p>挂在 Service/Controller 业务方法上，声明「这是什么业务操作」。切面据此产出准确的人话摘要，
 * 如"张三 审批了 运单 WBL-2024-001，状态 待审批→已通过"。
 *
 * <p>属性值可用 SpEL 从方法参数提取（如 {@code targetIdSpel = "#cmd.procInstId"}）。
 * 切面用 Spring 的 {@code SpelExpressionParser} + {@code DefaultParameterNameDiscoverer} 求值。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /** 业务动作名（中文），如"审批运单"。 */
    String operName() default "";

    /** 动作类型。 */
    AuditOperType operType() default AuditOperType.CUSTOM;

    /** 业务类型，如"freight_order"。为空则取实体名。 */
    String bizType() default "";

    /**
     * SpEL 取实体 Class（自动取 entityTitle/targetName）。
     * 形如 {@code "#order.class"} 或直接不填，由 targetId/bizNameColumn 配合元数据推断。
     */
    String entityClassSpel() default "";

    /** SpEL 取业务对象主键，如 {@code "#order.id"}。 */
    String targetIdSpel() default "";

    /** SpEL 取业务对象名称（业务编号），如 {@code "#order.orderNo"}。 */
    String targetNameSpel() default "";

    /** 直接指定业务名列（如"orderNo"），与 targetNameSpel 二选一。 */
    String bizNameColumn() default "";

    /** 是否记录字段级明细（回查旧值做 diff）。默认 true。 */
    boolean recordDetail() default true;

    /** 额外业务参数 SpEL（写入 metadata，如审批意见 {@code "#cmd.remark"}）。 */
    String[] extraSpel() default {};
}

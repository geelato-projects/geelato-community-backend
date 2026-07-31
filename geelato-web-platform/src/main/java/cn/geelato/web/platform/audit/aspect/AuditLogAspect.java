package cn.geelato.web.platform.audit.aspect;

import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.orm.Dao;
import cn.geelato.web.platform.audit.annotation.AuditLog;
import cn.geelato.web.platform.audit.boot.AuditLogProperties;
import cn.geelato.web.platform.audit.context.AuditContext;
import cn.geelato.web.platform.audit.enums.AuditCaptureLayer;
import cn.geelato.web.platform.audit.model.AuditFieldChange;
import cn.geelato.web.platform.audit.model.AuditLogRecord;
import cn.geelato.web.platform.audit.service.AuditBusinessNamer;
import cn.geelato.web.platform.audit.service.AuditContextProvider;
import cn.geelato.web.platform.audit.service.AuditDiffService;
import cn.geelato.web.platform.audit.service.AuditLogService;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 第1层声明式审计切面：处理 {@link AuditLog} 注解。
 *
 * <p>流程：执行前 SpEL 提取业务信息并 declare（供第2层去重）；按需回查旧值；执行业务方法；
 * 仅在业务方法成功返回后构建审计记录并异步落库（操作失败即数据未变更，不记录审计）。
 *
 * <p>业务异常照常抛出（不吞），仅审计落库失败被吞掉，绝不影响业务。
 */
@Slf4j
@Aspect
@Component
public class AuditLogAspect {

    private final AuditLogService auditLogService;
    private final AuditBusinessNamer namer;
    private final AuditDiffService diffService;
    private final AuditContextProvider contextProvider;
    private final AuditLogProperties properties;
    private final Dao dao;

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer pnd = new DefaultParameterNameDiscoverer();

    @Autowired
    public AuditLogAspect(AuditLogService auditLogService,
                          AuditBusinessNamer namer,
                          AuditDiffService diffService,
                          AuditContextProvider contextProvider,
                          AuditLogProperties properties,
                          @Qualifier("primaryDao") Dao dao) {
        this.auditLogService = auditLogService;
        this.namer = namer;
        this.diffService = diffService;
        this.contextProvider = contextProvider;
        this.properties = properties;
        this.dao = dao;
    }

    @Around("@annotation(auditLogAnno)")
    public Object around(ProceedingJoinPoint pjp, AuditLog auditLogAnno) throws Throwable {
        if (!properties.isEnabled()) {
            return pjp.proceed();
        }

        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        EvaluationContext ctx = buildContext(method, pjp.getArgs());

        // SpEL 提取业务信息
        Class<?> entityClass = evalClass(auditLogAnno.entityClassSpel(), ctx);
        String entityName = entityClass != null ? resolveEntityName(entityClass) : null;
        String bizType = StringUtils.hasText(auditLogAnno.bizType()) ? auditLogAnno.bizType() : entityName;
        String targetId = evalString(auditLogAnno.targetIdSpel(), ctx);
        String targetName = evalString(auditLogAnno.targetNameSpel(), ctx);

        // declare（供第2层去重）
        AuditContext.current().declare(auditLogAnno.operName(), bizType, targetId);

        // 回查旧值（若需要明细）
        Map<String, Object> before = null;
        if (auditLogAnno.recordDetail() && entityClass != null && StringUtils.hasText(targetId)) {
            before = queryById(entityClass, targetId);
        }

        long start = System.currentTimeMillis();
        AuditLogRecord record = auditLogService.create();
        record.setCaptureLayer(AuditCaptureLayer.ANNOTATED.name());
        record.setOperType(auditLogAnno.operType().name());
        record.setOperName(resolveOperName(auditLogAnno));
        record.setBizType(bizType);
        record.setEntityName(entityName);
        if (entityName != null) {
            record.setEntityTitle(namer.entityTitle(entityName));
            record.setTableName(namer.tableName(entityName));
        }
        record.setTargetId(targetId);
        record.setTargetName(targetName);
        record.setMethod(method.getDeclaringClass().getSimpleName() + "#" + method.getName());

        // extra 参数 → metadata
        Map<String, Object> extra = evalExtra(auditLogAnno.extraSpel(), ctx);
        if (extra != null && !extra.isEmpty()) {
            record.setMetadata(JSON.toJSONString(extra));
        }

        // 身份快照
        auditLogService.applyActor(record, contextProvider.snapshot());

        // 执行业务方法：成功才记审计；失败（抛异常）说明数据未变更，不记录审计
        Object result;
        try {
            result = pjp.proceed();
        } catch (Throwable ex) {
            // 操作失败，无数据变更，不记审计，原样抛出业务异常
            throw ex;
        }

        record.setDurationMs((int) (System.currentTimeMillis() - start));

        // 明细：回查新值做 diff
        try {
            if (auditLogAnno.recordDetail() && entityClass != null && StringUtils.hasText(targetId)) {
                Map<String, Object> after = queryById(entityClass, targetId);
                List<AuditFieldChange> changes = diffService.diff(entityName, before, after);
                record.setDetailJson(auditLogService.toJson(changes));
                record.setSummary(auditLogService.buildSummary(record, changes));
            } else {
                record.setSummary(auditLogService.buildSummary(record, null));
            }
        } catch (Exception e) {
            log.warn("审计明细构建失败 method={}", record.getMethod(), e);
            record.setSummary(auditLogService.buildSummary(record, null));
        }

        try {
            auditLogService.asyncLog(record);
        } catch (Exception e) {
            log.warn("审计落库失败 method={}", record.getMethod(), e);
        }
        return result;
    }

    private EvaluationContext buildContext(Method method, Object[] args) {
        return new MethodBasedEvaluationContext(null, method, args, pnd);
    }

    private String evalString(String spel, EvaluationContext ctx) {
        if (!StringUtils.hasText(spel)) {
            return null;
        }
        try {
            Object v = parse(spel).getValue(ctx);
            return v == null ? null : v.toString();
        } catch (Exception e) {
            log.debug("审计 SpEL 求值失败 spel={}", spel, e);
            return null;
        }
    }

    private Class<?> evalClass(String spel, EvaluationContext ctx) {
        if (!StringUtils.hasText(spel)) {
            return null;
        }
        try {
            Object v = parse(spel).getValue(ctx);
            if (v instanceof Class<?>) {
                return (Class<?>) v;
            }
            if (v != null) {
                return v.getClass();
            }
        } catch (Exception e) {
            log.debug("审计 SpEL 求值失败 spel={}", spel, e);
        }
        return null;
    }

    private Map<String, Object> evalExtra(String[] spels, EvaluationContext ctx) {
        if (spels == null || spels.length == 0) {
            return null;
        }
        Map<String, Object> map = new HashMap<>();
        for (String s : spels) {
            if (!StringUtils.hasText(s)) {
                continue;
            }
            try {
                Object v = parse(s).getValue(ctx);
                map.put(s, v);
            } catch (Exception e) {
                log.debug("审计 extra SpEL 求值失败 spel={}", s, e);
            }
        }
        return map;
    }

    private Expression parse(String spel) {
        return parser.parseExpression(spel);
    }

    /** 从 Class 解析实体名（@Entity.name 或类 simpleName）。 */
    private String resolveEntityName(Class<?> clazz) {
        cn.geelato.lang.meta.Entity eAnn = clazz.getAnnotation(cn.geelato.lang.meta.Entity.class);
        if (eAnn != null && StringUtils.hasText(eAnn.name())) {
            return eAnn.name();
        }
        return clazz.getSimpleName();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> queryById(Class<?> entityClass, String id) {
        try {
            List<Map<String, Object>> list = dao.queryForMapList(entityClass,
                    new FilterGroup().addFilter("id", id));
            return (list != null && !list.isEmpty()) ? list.get(0) : null;
        } catch (Exception ex) {
            log.debug("审计回查失败 class={} id={}", entityClass, id, ex);
            return null;
        }
    }

    /** 解析动作名：注解显式给则用，否则用 operType 的默认名。 */
    private String resolveOperName(AuditLog ann) {
        if (StringUtils.hasText(ann.operName())) {
            return ann.operName();
        }
        return ann.operType().defaultName();
    }
}

package cn.geelato.web.platform.srv.ormhook.service;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.orm.Dao;
import cn.geelato.meta.OrmHook;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.srv.ormhook.enums.HookLogStatusEnum;
import cn.geelato.web.platform.srv.ormhook.enums.OrmHookEventEnum;
import cn.geelato.web.platform.srv.ormhook.spi.OrmHookActionExecutor;
import cn.geelato.web.platform.srv.ormhook.spi.OrmHookActionManager;
import cn.geelato.web.platform.srv.platform.service.BaseService;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Set;

/**
 * 钩子配置服务：CRUD + 写入时硬校验（对齐"配置坏了就该在写入时被发现"的偏好，
 * 坏配置直接拒绝并指明原因，不静默入库）+ 写后刷新规则快照。
 * <p>
 * 动作类型校验：script 要求脚本内容非空（并做 JS 括号配对的轻量语法检查）；
 * http 要求地址非空、方法合法、请求头为合法 JSON 对象。
 *
 * @author geelato
 */
@Service
@Slf4j
public class OrmHookService extends BaseService {

    private static final Set<String> HTTP_METHODS = Set.of("GET", "POST", "PUT", "DELETE", "PATCH");

    private final OrmHookRegistry registry;
    private final OrmHookActionManager actionManager;

    @Autowired
    public OrmHookService(@Qualifier("primaryDao") Dao dao,
                          OrmHookRegistry registry,
                          OrmHookActionManager actionManager) {
        this.dao = dao;
        this.registry = registry;
        this.actionManager = actionManager;
    }

    public OrmHook createModel(OrmHook model) {
        validate(model);
        OrmHook saved = super.createModel(model);
        refreshQuietly();
        return saved;
    }

    public OrmHook updateModel(OrmHook model) {
        validate(model);
        OrmHook saved = super.updateModel(model);
        refreshQuietly();
        return saved;
    }

    public void isDeleteModel(OrmHook model) {
        super.isDeleteModel(model);
        refreshQuietly();
    }

    /**
     * 死信重放：CAS 将 dead 行重置为 ready（retry_count 清零、错误清空、立即到期）。
     *
     * @return 是否重放成功（行不存在或非 dead 状态返回 false）
     */
    public boolean replayDead(String hookLogId) {
        if (StringUtils.isBlank(hookLogId)) {
            return false;
        }
        int n = dao.getJdbcTemplate().update(
                "UPDATE platform_orm_hook_log SET status = ?, retry_count = 0, next_retry_at = NULL, "
                        + "error_msg = NULL, update_at = ? WHERE id = ? AND status = ?",
                HookLogStatusEnum.READY.value(), new Date(), hookLogId, HookLogStatusEnum.DEAD.value());
        if (n > 0) {
            log.info("ORM Hook 死信已重放 hookLogId={}", hookLogId);
        }
        return n > 0;
    }

    /** 写入时硬校验：不合法直接抛 {@link IllegalArgumentException}，消息含可操作提示。 */
    private void validate(OrmHook model) {
        if (model == null) {
            throw new IllegalArgumentException("钩子配置不能为空。");
        }
        if (StringUtils.isBlank(model.getTitle())) {
            throw new IllegalArgumentException("钩子名称（title）不能为空。");
        }
        String entityName = model.getEntityName();
        if (StringUtils.isBlank(entityName)) {
            throw new IllegalArgumentException("目标实体名称（entityName）不能为空。");
        }
        if (OrmHookRegistry.FORBIDDEN_TARGET_ENTITIES.contains(entityName)) {
            throw new IllegalArgumentException(String.format(
                    "禁止对钩子机制自身的表配置钩子：%s（防自触发）。", entityName));
        }
        if (OrmHookEventEnum.of(model.getEventType()) == null) {
            throw new IllegalArgumentException(String.format(
                    "非法事件类型：%s（合法值：insert | update | delete）。", model.getEventType()));
        }
        String actionType = model.getActionType();
        if (StringUtils.isBlank(actionType)) {
            throw new IllegalArgumentException("动作类型（actionType）不能为空。");
        }
        if (!actionManager.supports(actionType)) {
            throw new IllegalArgumentException(String.format(
                    "动作类型无可用执行器：%s（当前支持：script | http）。", actionType));
        }
        try {
            if (MetaManager.singleInstance().getByEntityName(entityName) == null) {
                throw new IllegalArgumentException(String.format(
                        "实体 %s 不存在于元数据，请确认 entityName（实体名而非表名）。", entityName));
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format(
                    "解析实体 %s 元数据失败：%s", entityName, e.getMessage()), e);
        }
        if (OrmHookActionExecutor.TYPE_SCRIPT.equals(actionType)) {
            validateScript(model);
        } else if (OrmHookActionExecutor.TYPE_HTTP.equals(actionType)) {
            validateHttp(model);
        }
    }

    /** script 动作：脚本内容非空 + 括号配对轻量检查（完整语法由首次执行的 GraalJS 报错兜底）。 */
    private void validateScript(OrmHook model) {
        String script = model.getScriptContent();
        if (StringUtils.isBlank(script)) {
            throw new IllegalArgumentException("script 动作缺少脚本内容：请编写函数体 function(){ ... }。");
        }
        if (!script.contains("function")) {
            throw new IllegalArgumentException(
                    "脚本内容应为函数体定义：function(){ ... }，触发载荷经闭包中的 parameter 获取。");
        }
        long opens = script.chars().filter(ch -> ch == '{').count();
        long closes = script.chars().filter(ch -> ch == '}').count();
        if (opens != closes) {
            throw new IllegalArgumentException(String.format(
                    "脚本内容大括号不配对（{ %d 个、} %d 个），请检查语法。", opens, closes));
        }
    }

    /** http 动作：地址必填、方法合法、请求头为合法 JSON 对象。 */
    private void validateHttp(OrmHook model) {
        if (StringUtils.isBlank(model.getHttpUrl())) {
            throw new IllegalArgumentException("http 动作缺少接口地址（httpUrl）。");
        }
        String method = model.getHttpMethod();
        if (StringUtils.isBlank(method)) {
            model.setHttpMethod("POST");
        } else if (!HTTP_METHODS.contains(method.trim().toUpperCase())) {
            throw new IllegalArgumentException(String.format(
                    "非法 HTTP 方法：%s（合法值：%s）。", method, String.join(" | ", HTTP_METHODS)));
        } else {
            model.setHttpMethod(method.trim().toUpperCase());
        }
        String headers = model.getHttpHeaders();
        if (StringUtils.isNotBlank(headers)) {
            try {
                JSON.parseObject(headers);
            } catch (Exception e) {
                throw new IllegalArgumentException("HTTP 请求头须为合法 JSON 对象，如 {\"Authorization\":\"Bearer x\"}。");
            }
        }
    }

    /** 写后刷新快照；失败保留旧快照并留痕（新写入的配置经 validate 校验，刷新失败多为基础设施异常）。 */
    private void refreshQuietly() {
        try {
            registry.refresh();
        } catch (Exception e) {
            log.error("ORM 钩子规则快照刷新失败，保留旧快照：{}", e.getMessage(), e);
        }
    }
}

package cn.geelato.web.platform.srv.ormhook.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.meta.OrmHook;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.srv.ormhook.enums.HookLogStatusEnum;
import cn.geelato.web.platform.srv.platform.service.BaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Date;

/**
 * 钩子配置服务：CRUD + 写入时硬校验（对齐"配置坏了就该在写入时被发现"的偏好，
 * 坏配置直接拒绝并指明原因，不静默入库）+ 写后刷新钩子快照。
 * <p>
 * 模型（v3 定案）：一条 Hook = 名称+地址+启停，校验即 标题必填、地址必填且为合法 http/https URL。
 *
 * @author geelato
 */
@Service
@Slf4j
public class OrmHookService extends BaseService {

    private final OrmHookRegistry registry;
    private final OrmHookLogProcessor logProcessor;

    @Autowired
    public OrmHookService(@Qualifier("primaryDao") Dao dao,
                          OrmHookRegistry registry,
                          OrmHookLogProcessor logProcessor) {
        this.dao = dao;
        this.registry = registry;
        this.logProcessor = logProcessor;
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
     * 死信重放：CAS 将 dead 行重置为 ready（retry_count 清零、错误清空、立即到期），
     * 并登记内存定时立即拾取（不等兜底扫描）。
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
            logProcessor.scheduleRetry(hookLogId, 0);
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
        String url = model.getHttpUrl();
        if (StringUtils.isBlank(url)) {
            throw new IllegalArgumentException("地址（httpUrl）不能为空。");
        }
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("地址须以 http:// 或 https:// 开头。");
            }
            if (StringUtils.isBlank(uri.getHost())) {
                throw new IllegalArgumentException("地址缺少主机名，如 http://host:port/path。");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("地址格式不合法：" + url);
        }
    }

    /** 写后刷新快照；失败保留旧快照并留痕（新写入的配置经 validate 校验，刷新失败多为基础设施异常）。 */
    private void refreshQuietly() {
        try {
            registry.refresh();
        } catch (Exception e) {
            log.error("ORM 钩子快照刷新失败，保留旧快照：{}", e.getMessage(), e);
        }
    }
}

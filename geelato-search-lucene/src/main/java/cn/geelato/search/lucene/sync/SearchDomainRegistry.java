package cn.geelato.search.lucene.sync;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.search.lucene.config.GeelatoSearchProperties.DomainConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 搜索域注册表：配置驱动 + 编程注册。
 *
 * <p>索引同步按写事件实体归属解析（主实体直接定位文档，子实体经外键归属主文档）；
 * 路由按查询实体解析（主实体直查 + routeEntities 视图映射）。
 * 域配置在注册时做结构校验（实体存在、字段可解析），非法域拒绝注册并告警——
 * 不静默生效，避免半配置状态下索引与路由行为不一致。
 */
@Slf4j
public class SearchDomainRegistry {

    private final MetaManager metaManager = MetaManager.singleInstance();

    private final Map<String, DomainConfig> domainsById = new ConcurrentHashMap<>();
    private final Map<String, DomainConfig> domainsByMainEntity = new ConcurrentHashMap<>();
    private final Map<String, DomainConfig> domainsByChildEntity = new ConcurrentHashMap<>();

    public void register(DomainConfig domain) {
        validate(domain);
        domainsById.put(domain.getId(), domain);
        domainsByMainEntity.put(domain.getMainEntity(), domain);
        for (DomainConfig.ChildConfig child : domain.getChildren()) {
            domainsByChildEntity.put(child.getEntity(), domain);
        }
        log.info("注册搜索域: id={}, mainEntity={}, fields={}, children={}, routeEntities={}",
                domain.getId(), domain.getMainEntity(), domain.getFields(),
                domain.getChildren().size(), domain.getRouteEntities().keySet());
    }

    public void registerAll(List<DomainConfig> domainConfigs) {
        if (domainConfigs == null) {
            return;
        }
        domainConfigs.forEach(this::register);
    }

    public boolean hasDomains() {
        return !domainsById.isEmpty();
    }

    public java.util.Collection<DomainConfig> allDomains() {
        return domainsById.values();
    }

    public DomainConfig byId(String domainId) {
        return domainsById.get(domainId);
    }

    public DomainConfig byMainEntity(String entityName) {
        return domainsByMainEntity.get(entityName);
    }

    /** 写事件实体归属：主实体返回 (domain, null)；子实体返回 (domain, child)；不属于任何域返回 null。 */
    public SyncEntityMatch resolveSyncEntity(String entityName) {
        DomainConfig domain = domainsByMainEntity.get(entityName);
        if (domain != null) {
            return new SyncEntityMatch(domain, null);
        }
        domain = domainsByChildEntity.get(entityName);
        if (domain != null) {
            for (DomainConfig.ChildConfig child : domain.getChildren()) {
                if (child.getEntity().equals(entityName)) {
                    return new SyncEntityMatch(domain, child);
                }
            }
        }
        return null;
    }

    /**
     * 查询实体路由解析：mainEntity 直查（视图列名即主实体列名，经元数据转字段名），
     * 或 routeEntities 中显式配置的视图映射。
     */
    public RouteEntityMatch resolveRouteEntity(String entityName) {
        DomainConfig domain = domainsByMainEntity.get(entityName);
        if (domain != null) {
            return new RouteEntityMatch(domain, entityName, domain.getPkField(), null);
        }
        for (DomainConfig d : domainsById.values()) {
            DomainConfig.RouteEntityConfig re = d.getRouteEntities().get(entityName);
            if (re != null) {
                return new RouteEntityMatch(d, entityName, null, re);
            }
        }
        return null;
    }

    /**
     * 域配置校验。
     *
     * <p>结构错误（缺 id/mainEntity/fields）硬失败——纯配置问题启动即阻断；
     * 元数据存在性（实体/字段是否在 MetaManager 中）只告警不阻断——平台元数据
     * 有时间维度（启动为 CLASS 冲突策略版本，运行期 refreshDBMeta 后为 DB 完整版本），
     * 启动期严格校验会误杀合法配置。运行期路由/回表对缺失字段自然放弃并记原因日志，
     * 真配置错误会在每次相关查询的日志中持续暴露。
     */
    private void validate(DomainConfig domain) {
        if (domain.getId() == null || domain.getId().isBlank()) {
            throw new IllegalArgumentException("搜索域缺少 id");
        }
        if (domain.getMainEntity() == null || domain.getMainEntity().isBlank()) {
            throw new IllegalArgumentException("搜索域 " + domain.getId() + " 缺少 mainEntity");
        }
        if (domain.getFields() == null || domain.getFields().isEmpty()) {
            throw new IllegalArgumentException("搜索域 " + domain.getId() + " 未配置编号字段");
        }
        EntityMeta em = metaManager.getByEntityName(domain.getMainEntity());
        if (em == null) {
            log.warn("搜索域 {} 主实体元数据暂不存在（可能未加载完成，运行期路由将跳过）: {}",
                    domain.getId(), domain.getMainEntity());
        } else {
            warnMissingFields(domain.getId(), em, domain.getMainEntity(), domain.getFields());
        }
        for (DomainConfig.ChildConfig child : domain.getChildren()) {
            EntityMeta cem = metaManager.getByEntityName(child.getEntity());
            if (cem == null) {
                log.warn("搜索域 {} 子实体元数据暂不存在（可能未加载完成，相关同步/路由将跳过）: {}",
                        domain.getId(), child.getEntity());
                continue;
            }
            warnMissingField(domain.getId(), cem, child.getEntity(), child.getFkField());
            warnMissingFields(domain.getId(), cem, child.getEntity(), child.getFields());
        }
        for (Map.Entry<String, DomainConfig.RouteEntityConfig> e : domain.getRouteEntities().entrySet()) {
            if (metaManager.getByEntityName(e.getKey()) == null) {
                log.warn("搜索域 {} 路由实体元数据暂不存在（可能未加载完成，相关查询将走 SQL 兜底）: {}",
                        domain.getId(), e.getKey());
            }
        }
    }

    private void warnMissingFields(String domainId, EntityMeta em, String entityName, List<String> fields) {
        for (String field : fields) {
            warnMissingField(domainId, em, entityName, field);
        }
    }

    private void warnMissingField(String domainId, EntityMeta em, String entityName, String field) {
        if (field == null || !em.containsField(field)) {
            log.warn("搜索域 {} 实体 {} 字段在元数据中暂不存在（可能加载时序或名称不符；" +
                            "运行期路由/回表将跳过该字段并记原因日志）: {}",
                    domainId, entityName, field);
        }
    }

    /** 写事件归属结果。 */
    public record SyncEntityMatch(DomainConfig domain, DomainConfig.ChildConfig child) {
    }

    /**
     * 查询路由归属结果。viewConfig 为 null 表示 mainEntity 直查（列名→字段名经元数据），
     * 非空按视图 columnMap 映射。
     */
    public record RouteEntityMatch(DomainConfig domain, String entityName,
                                   String pkFieldDirect, DomainConfig.RouteEntityConfig viewConfig) {
    }
}

package cn.geelato.search.lucene.config;

import cn.geelato.core.orm.Dao;
import cn.geelato.core.orm.event.DeleteEventManager;
import cn.geelato.core.orm.event.SaveEventManager;
import cn.geelato.search.api.SearchEngine;
import cn.geelato.search.lucene.compensate.SearchCompensationTask;
import cn.geelato.search.lucene.compensate.SearchReconcileTask;
import cn.geelato.search.lucene.compensate.SearchSyncFailStore;
import cn.geelato.search.lucene.engine.LuceneSearchEngine;
import cn.geelato.search.lucene.reindex.SearchReindexService;
import cn.geelato.search.lucene.route.LuceneFuzzymatchRouter;
import cn.geelato.search.lucene.sync.DocumentLoader;
import cn.geelato.search.lucene.sync.SearchDomainRegistry;
import cn.geelato.search.lucene.sync.SearchSyncDeleteListener;
import cn.geelato.search.lucene.sync.SearchSyncSaveListener;
import cn.geelato.search.lucene.sync.SearchSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;
import java.util.List;

/**
 * 搜索模块自动装配。
 *
 * <p>装配顺序：引擎 → 域注册（配置校验，非法域硬失败）→ 补偿表初始化 →
 * 同步组件（监听器挂到 ORM 既有事件管理器，容器销毁时注销）→ 补偿/对账任务 →
 * reindex → 路由器（MqlFuzzymatchRouter bean，MqlQueryProcessor 经 SPI 发现）。
 *
 * <p>{@code geelato.search.enabled=false} 时不装配任何组件：
 * 查询走 SQL REGEXP 等价改写兜底，写入零额外开销。
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(GeelatoSearchProperties.class)
@ConditionalOnProperty(prefix = "geelato.search", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GeelatoSearchAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(SearchEngine.class)
    public LuceneSearchEngine luceneSearchEngine(GeelatoSearchProperties properties) {
        return new LuceneSearchEngine(properties.getIndexRootDir(), properties.getMaxIds());
    }

    @Bean
    public SearchDomainRegistry searchDomainRegistry(GeelatoSearchProperties properties) {
        SearchDomainRegistry registry = new SearchDomainRegistry();
        registry.registerAll(properties.getDomains());
        if (properties.getDomains().isEmpty()) {
            log.info("未配置搜索域（geelato.search.domains），索引同步与路由均不生效");
        }
        return registry;
    }

    /**
     * 回表/补偿表用的 JdbcTemplate。
     * <p>平台存在多个 Dao（primaryDao / dbGenerateDao / dynamicDao）与多个 DataSource
     * （primaryDataSource / dynamicDataSource），且均无 @Primary，不能按单一类型注入。
     * 取用优先级：
     * <ol>
     *   <li>dynamicDao 的 JdbcTemplate——切库路由只挂 dynamicDao，回表与业务写入同链路；</li>
     *   <li>其余任一 Dao 的 JdbcTemplate（dynamicDao 未装配时）；</li>
     *   <li>dynamicDataSource（AbstractRoutingDataSource）——异步线程无路由 key 时
     *       由平台兜底链落回 primary；</li>
     *   <li>其余任一可用 DataSource。</li>
     * </ol>均不可得时显式抛错（搜索域回表无数据源无法工作，不静默降级）。
     */
    @Bean
    public JdbcTemplate searchJdbcTemplate(org.springframework.beans.factory.BeanFactory beanFactory,
                                           ObjectProvider<Dao> daoProvider,
                                           ObjectProvider<DataSource> dataSourceProvider) {
        // Spring 6.0 的 ObjectProvider 无按名获取重载，经 BeanFactory 精确取 dynamicDao
        Dao dao = null;
        if (beanFactory.containsBean("dynamicDao")) {
            dao = beanFactory.getBean("dynamicDao", Dao.class);
        }
        if (dao == null) {
            dao = daoProvider.orderedStream().findFirst().orElse(null);
        }
        if (dao != null && dao.getJdbcTemplate() != null) {
            return dao.getJdbcTemplate();
        }
        List<DataSource> candidates = dataSourceProvider.orderedStream().toList();
        for (DataSource candidate : candidates) {
            if (candidate instanceof AbstractRoutingDataSource) {
                return new JdbcTemplate(candidate);
            }
        }
        if (!candidates.isEmpty()) {
            return new JdbcTemplate(candidates.get(0));
        }
        throw new IllegalStateException(
                "搜索模块需要可用 DataSource（优先 dynamicDao#jdbcTemplate 或 dynamicDataSource），但容器中不存在任何 DataSource bean");
    }

    @Bean
    public SearchSyncFailStore searchSyncFailStore(JdbcTemplate searchJdbcTemplate) {
        SearchSyncFailStore store = new SearchSyncFailStore(searchJdbcTemplate);
        store.initSchema();
        return store;
    }

    @Bean
    public DocumentLoader searchDocumentLoader(JdbcTemplate searchJdbcTemplate) {
        return new DocumentLoader(searchJdbcTemplate);
    }

    @Bean
    public SearchSyncService searchSyncService(SearchEngine searchEngine, DocumentLoader documentLoader) {
        return new SearchSyncService(searchEngine, documentLoader);
    }

    @Bean
    public SearchSyncSaveListener searchSyncSaveListener(SearchDomainRegistry registry,
                                                          SearchSyncService syncService,
                                                          SearchSyncFailStore failStore) {
        SearchSyncSaveListener listener = new SearchSyncSaveListener(registry, syncService, failStore);
        SaveEventManager.registerAfterIfAbsent(listener);
        return listener;
    }

    @Bean
    public SearchSyncDeleteListener searchSyncDeleteListener(SearchDomainRegistry registry,
                                                              SearchSyncService syncService,
                                                              SearchSyncFailStore failStore) {
        SearchSyncDeleteListener listener = new SearchSyncDeleteListener(registry, syncService, failStore);
        DeleteEventManager.registerAfterIfAbsent(listener);
        return listener;
    }

    @Bean
    public SearchCompensationTask searchCompensationTask(SearchDomainRegistry registry,
                                                          SearchSyncService syncService,
                                                          SearchSyncFailStore failStore,
                                                          GeelatoSearchProperties properties) {
        return new SearchCompensationTask(registry, syncService, failStore, properties);
    }

    @Bean
    public SearchReconcileTask searchReconcileTask(SearchDomainRegistry registry,
                                                    SearchSyncService syncService,
                                                    SearchSyncFailStore failStore,
                                                    GeelatoSearchProperties properties) {
        return new SearchReconcileTask(registry, syncService, failStore, properties);
    }

    @Bean(destroyMethod = "shutdown")
    public SearchReindexService searchReindexService(SearchDomainRegistry registry,
                                                      DocumentLoader documentLoader,
                                                      SearchEngine searchEngine,
                                                      cn.geelato.search.lucene.sync.SearchSyncService syncService,
                                                      GeelatoSearchProperties properties) {
        return new SearchReindexService(registry, documentLoader, searchEngine, syncService, properties);
    }

    @Bean
    public LuceneFuzzymatchRouter luceneFuzzymatchRouter(SearchDomainRegistry registry,
                                                          LuceneSearchEngine engine,
                                                          GeelatoSearchProperties properties) {
        return new LuceneFuzzymatchRouter(registry, engine, properties);
    }

    /**
     * 仅当组件扫描未覆盖（应用扫描范围不含本包）时由自动装配兜底注册。
     * 类上的 @ApiRuntimeRestController 组合 @RestController，在被扫描的应用里
     * 会先注册为用户 bean，此处按类型退让避免同名冲突。
     */
    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnMissingBean(cn.geelato.search.lucene.web.SearchManageController.class)
    public cn.geelato.search.lucene.web.SearchManageController searchManageController(
            SearchDomainRegistry registry, SearchReindexService reindexService,
            LuceneSearchEngine engine, SearchSyncFailStore failStore,
            GeelatoSearchProperties properties) {
        return new cn.geelato.search.lucene.web.SearchManageController(
                registry, reindexService, engine, failStore, properties);
    }
}

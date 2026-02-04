package cn.geelato.web.platform.event;

import cn.geelato.core.orm.event.SaveEventContext;
import cn.geelato.core.orm.event.AfterSaveEventListener;
import cn.geelato.web.platform.run.SpringContextHolder;
import org.springframework.core.env.Environment;
import cn.geelato.web.platform.boot.properties.EsConfigurationProperties;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import org.springframework.jdbc.core.JdbcTemplate;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class EsSyncSaveListener implements AfterSaveEventListener {
    private volatile ElasticsearchClient client;

    @Override
    public boolean supports(SaveEventContext context) {
        return context.getCommand() != null;
    }
    @Override
    public boolean enabled(SaveEventContext context) {
        return getClient() != null;
    }

    @Override
    public void beforeSave(SaveEventContext context) {}

    @Override
    public void afterSave(SaveEventContext context) {
        String entityName = context.getCommand().getEntityName();
        String pk = context.getCommand().getPK();
        String indexPrefix = getProps().getIndexPrefix();
        String baseIndex = entityName == null ? "default" : entityName.toLowerCase();
        String index = (indexPrefix == null || indexPrefix.isEmpty()) ? baseIndex : (indexPrefix + baseIndex);
        try {
            if (log.isInfoEnabled()) {
                log.info("es-sync start, eventId={}, index={}", context.getEventId(), index);
            }
            ElasticsearchClient c = getClient();
            EntityMeta em = MetaManager.singleInstance().getByEntityName(entityName);
            String table = em.getTableName();
            JdbcTemplate jt = context.getDao().getJdbcTemplate();
            Map<String, Object> row = jt.queryForMap("select * from " + table + " where id = ?", pk);
            IndexRequest<Map<String, Object>> req = IndexRequest.of(b -> b
                    .index(index)
                    .id(pk)
                    .document(row)
            );
            c.index(req);
            if (log.isInfoEnabled()) {
                log.info("es-sync done, eventId={}, index={}", context.getEventId(), index);
            }
        } catch (Exception ignored) {
            log.error("es-sync error, eventId={}, index={}", context.getEventId(), index, ignored);
        }
    }

    private ElasticsearchClient getClient() {
        if (client != null) return client;
        synchronized (this) {
            if (client != null) return client;
            try {
                client = SpringContextHolder.getBean(ElasticsearchClient.class);
            } catch (Exception e) {
                client = null;
            }
            return client;
        }
    }

    private EsConfigurationProperties getProps() {
        return SpringContextHolder.getBean(EsConfigurationProperties.class);
    }
}

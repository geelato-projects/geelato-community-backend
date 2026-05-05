package cn.geelato.web.platform.event;

import cn.geelato.core.orm.event.SaveEventContext;
import cn.geelato.core.orm.event.AfterSaveEventListener;
import cn.geelato.web.platform.run.SpringContextHolder;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import org.springframework.jdbc.core.JdbcTemplate;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class EsSyncSaveListener implements AfterSaveEventListener {
    private static final boolean ES_SYNC_ENABLED = false;
    private static final String ES_INDEX_PREFIX = "";
    private volatile ElasticsearchClient client;

    @Override
    public boolean supports(SaveEventContext context) {
        return context.getCommand() != null;
    }
    @Override
    public boolean enabled(SaveEventContext context) {
        return ES_SYNC_ENABLED && getClient() != null;
    }

    @Override
    public void beforeSave(SaveEventContext context) {}

    @Override
    public void afterSave(SaveEventContext context) {
        String entityName = context.getCommand().getEntityName();
        String pk = context.getCommand().getPK();
        String baseIndex = entityName == null ? "default" : entityName.toLowerCase();
        String index = (ES_INDEX_PREFIX == null || ES_INDEX_PREFIX.isEmpty()) ? baseIndex : (ES_INDEX_PREFIX + baseIndex);
        try {
            if (log.isInfoEnabled()) {
                log.info("es-sync start, eventId={}, index={}", context.getEventId(), index);
            }
            ElasticsearchClient c = getClient();
            EntityMeta em = MetaManager.singleInstance().getByEntityName(entityName);
            String table = em.getTableName();
            JdbcTemplate jt = context.getDao().getJdbcTemplate();
            Map<String, Object> row = jt.queryForMap("select * from " + table + " where id = ?", pk);
            UpdateRequest<Map<String, Object>, Map<String, Object>> req = UpdateRequest.of(b -> b
                    .index(index)
                    .id(pk)
                    .doc(row)
                    .docAsUpsert(true)
            );
            c.update(req, Map.class);
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

}

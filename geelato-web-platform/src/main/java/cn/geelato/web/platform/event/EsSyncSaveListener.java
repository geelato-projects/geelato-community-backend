package cn.geelato.web.platform.event;

import cn.geelato.core.orm.event.SaveEventContext;
import cn.geelato.core.orm.event.AfterSaveEventListener;
import cn.geelato.web.platform.run.SpringContextHolder;
import org.springframework.core.env.Environment;
import cn.geelato.web.platform.boot.properties.EsConfigurationProperties;
import com.alibaba.fastjson2.JSONObject;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import java.net.URI;
import java.util.Base64;

import java.util.Map;

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
        Map<String, Object> data = context.getDao().queryByEntityNameAndPK(entityName, pk);
        String indexPrefix = getProps().getIndexPrefix();
        String baseIndex = entityName == null ? "default" : entityName.toLowerCase();
        String index = (indexPrefix == null || indexPrefix.isEmpty()) ? baseIndex : (indexPrefix + baseIndex);
        try {
            ElasticsearchClient c = getClient();
            IndexRequest<Map<String, Object>> req = IndexRequest.of(b -> b
                    .index(index)
                    .id(pk)
                    .document(data)
            );
            c.index(req);
        } catch (Exception ignored) {
        }
    }

    private Environment getEnv() {
        return SpringContextHolder.getBean(Environment.class);
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

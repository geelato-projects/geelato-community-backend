package cn.geelato.web.platform.boot.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.elasticsearch.core.UpdateResponse;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.JsonpSerializable;
import jakarta.json.stream.JsonGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.StringWriter;

@Slf4j
@Component
public class EsOperations {
    private final ElasticsearchClient client;
    private final JsonpMapper jsonpMapper;
    private final boolean debugEnabled;
    private final int debugMaxLength;

    public EsOperations(ElasticsearchClient client,
                        JsonpMapper esJsonpMapper,
                        @Value("${geelato.es.debug:false}") boolean debugEnabled,
                        @Value("${geelato.es.debug-max-length:10000}") int debugMaxLength) {
        this.client = client;
        this.jsonpMapper = esJsonpMapper;
        this.debugEnabled = debugEnabled;
        this.debugMaxLength = debugMaxLength;
    }

    public ElasticsearchClient client() {
        return client;
    }

    public <T> SearchResponse<T> search(SearchRequest req, Class<T> tClass) throws Exception {
        debug("search.request", req);
        SearchResponse<T> resp = client.search(req, tClass);
        debug("search.response", resp);
        return resp;
    }

    public <TDocument> IndexResponse index(IndexRequest<TDocument> req) throws Exception {
        debug("index.request", req);
        IndexResponse resp = client.index(req);
        debug("index.response", resp);
        return resp;
    }

    public BulkResponse bulk(BulkRequest req) throws Exception {
        debug("bulk.request", req);
        BulkResponse resp = client.bulk(req);
        debug("bulk.response", resp);
        return resp;
    }

    public <TDocument, TPartialDocument> UpdateResponse<TDocument> update(UpdateRequest<TDocument, TPartialDocument> req, Class<?> tClass) throws Exception {
        debug("update.request", req);
        @SuppressWarnings("unchecked")
        Class<TDocument> c = (Class<TDocument>) tClass;
        UpdateResponse<TDocument> resp = client.update(req, c);
        debug("update.response", resp);
        return resp;
    }

    private void debug(String action, Object v) {
        if (!debugEnabled || !log.isDebugEnabled()) {
            return;
        }
        try {
            String text;
            if (v instanceof JsonpSerializable js) {
                text = toJson(js);
            } else {
                text = String.valueOf(v);
            }
            log.debug("[es] {} {}", action, truncate(text));
        } catch (Exception e) {
            log.debug("[es] {} <unprintable>", action);
        }
    }

    private String toJson(JsonpSerializable v) {
        StringWriter sw = new StringWriter();
        JsonGenerator g = jsonpMapper.jsonProvider().createGenerator(sw);
        v.serialize(g, jsonpMapper);
        g.close();
        return sw.toString();
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        int max = Math.max(debugMaxLength, 0);
        if (max <= 0 || s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }
}

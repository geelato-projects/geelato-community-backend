package cn.geelato.web.platform.logging.es;

import cn.geelato.web.platform.boot.properties.EsConfigurationProperties;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class EsLogDrainService {
    private final ElasticsearchClient elasticsearchClient;
    private final EsConfigurationProperties esProperties;
    private final EsLogIndexRouter indexRouter = new EsLogIndexRouter();
    private ScheduledExecutorService scheduler;

    public EsLogDrainService(ElasticsearchClient elasticsearchClient, EsConfigurationProperties esProperties) {
        this.elasticsearchClient = elasticsearchClient;
        this.esProperties = esProperties;
    }

    @PostConstruct
    public void init() {
        if (!Boolean.TRUE.equals(esProperties.getLogEnabled())) {
            log.info("es-log drain disabled.");
            return;
        }
        EsLogBuffer.resize(esProperties.getLogQueueSize());
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("es-log-drain");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::drain, esProperties.getLogFlushIntervalMs(),
                esProperties.getLogFlushIntervalMs(), TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void drain() {
        if (elasticsearchClient == null || !Boolean.TRUE.equals(esProperties.getLogEnabled())) {
            return;
        }
        List<EsLogEvent> batch = EsLogBuffer.pollBatch(esProperties.getLogBulkSize());
        if (batch.isEmpty()) {
            return;
        }
        long dropped = EsLogBuffer.drainDroppedCount();
        if (dropped > 0) {
            log.warn("es-log dropped {}", dropped);
        }
        int retry = 0;
        while (retry <= esProperties.getLogMaxRetry()) {
            try {
                BulkRequest.Builder builder = new BulkRequest.Builder();
                for (EsLogEvent event : batch) {
                    String index = indexRouter.route(event.loggerName(), esProperties.getLogIndexPrefix());
                    Map<String, Object> document = event.toDocument(esProperties.getLogAppName(), esProperties.getLogEnv());
                    builder.operations(op -> op.index(idx -> idx.index(index).document(document)));
                }
                elasticsearchClient.bulk(builder.build());
                return;
            } catch (Exception e) {
                retry++;
                if (retry > esProperties.getLogMaxRetry()) {
                    log.error("es-log drain failed, batchSize={}", batch.size(), e);
                    return;
                }
                try {
                    Thread.sleep(esProperties.getLogRetryBackoffMs());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}

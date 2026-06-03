package cn.geelato.web.platform.srvlog.store;

import cn.geelato.web.platform.srvlog.boot.SrvLogProperties;
import cn.geelato.web.platform.srvlog.model.SrvExceptionSummary;
import cn.geelato.web.platform.srvlog.model.SrvLogPage;
import cn.geelato.web.platform.srvlog.model.SrvLogRecord;
import cn.geelato.web.platform.srvlog.spi.SrvLogQueryOptions;
import cn.geelato.web.platform.srvlog.spi.SrvLogStore;
import cn.geelato.web.platform.srvlog.spi.SrvSummaryQueryOptions;
import cn.geelato.web.platform.boot.es.EsOperations;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonData;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "geelato.es", name = "enabled", havingValue = "true")
@ConditionalOnBean(EsOperations.class)
public class EsSrvLogStore implements SrvLogStore {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final EsOperations es;
    private final SrvLogProperties properties;

    public EsSrvLogStore(EsOperations es, SrvLogProperties properties) {
        this.es = es;
        this.properties = properties;
    }

    @Override
    public void save(SrvLogRecord record) {
        if (es == null || es.client() == null) {
            return;
        }
        String index = properties.getEsIndexPrefix() + LocalDate.now().format(DATE_FORMATTER);
        try {
            IndexRequest<SrvLogRecord> req = IndexRequest.of(i -> i.index(index).id(record.getId()).document(record));
            es.index(req);
        } catch (Exception ignored) {
        }
    }

    @Override
    public SrvLogPage listExceptions(String methodKey, SrvLogQueryOptions options) {
        if (es == null || es.client() == null) {
            SrvLogPage p = new SrvLogPage();
            p.setTotal(0);
            p.setPage(options.getPage());
            p.setSize(options.getSize());
            p.setRecords(Collections.emptyList());
            return p;
        }
        int page = Math.max(options.getPage(), 1);
        int size = Math.max(options.getSize(), 1);
        int from = Math.max((page - 1) * size, 0);

        Query q = buildExceptionQuery(methodKey, options.getStartTime(), options.getEndTime());
        SearchRequest req = SearchRequest.of(s -> s
                .index(properties.getEsIndexPrefix() + "*")
                .from(from)
                .size(size)
                .trackTotalHits(t -> t.enabled(true))
                .sort(so -> so.field(f -> f.field("timestamp").order(SortOrder.Desc)))
                .query(q)
        );
        try {
            SearchResponse<SrvLogRecord> resp = es.search(req, SrvLogRecord.class);
            List<SrvLogRecord> records = new ArrayList<>();
            if (resp.hits() != null && resp.hits().hits() != null) {
                resp.hits().hits().forEach(h -> {
                    if (h.source() != null) {
                        records.add(h.source());
                    }
                });
            }
            long total = resp.hits() != null && resp.hits().total() != null ? resp.hits().total().value() : records.size();
            SrvLogPage p = new SrvLogPage();
            p.setTotal(total);
            p.setPage(page);
            p.setSize(size);
            p.setRecords(records);
            return p;
        } catch (Exception ignored) {
            SrvLogPage p = new SrvLogPage();
            p.setTotal(0);
            p.setPage(page);
            p.setSize(size);
            p.setRecords(Collections.emptyList());
            return p;
        }
    }

    @Override
    public List<SrvExceptionSummary> listRecentExceptionSummary(int days, SrvSummaryQueryOptions options) {
        if (es == null || es.client() == null) {
            return Collections.emptyList();
        }
        int topN = options == null ? 200 : Math.max(options.getTopN(), 1);
        int termsSize = Math.min(Math.max(topN * 5, topN), 5000);
        long startTime = LocalDate.now().minusDays(Math.max(days, 0L)).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        Query q = buildRecentSummaryQuery(startTime);
        SearchRequest req = SearchRequest.of(s -> s
                .index(properties.getEsIndexPrefix() + "*")
                .size(0)
                .query(q)
                .aggregations("by_method", a -> a
                        .terms(t -> t.field("methodKey.keyword").size(termsSize))
                        .aggregations("last_call_ts", sub -> sub.max(m -> m.field("timestamp")))
                        .aggregations("exception_filter", sub -> sub
                                .filter(f -> f.term(t -> t.field("success").value(false)))
                                .aggregations("last_exception_ts", sx -> sx.max(m -> m.field("timestamp")))
                        )
                )
        );
        try {
            SearchResponse<Void> resp = es.search(req, Void.class);
            Aggregate agg = resp.aggregations() == null ? null : resp.aggregations().get("by_method");
            if (agg == null) {
                return Collections.emptyList();
            }
            StringTermsAggregate terms = agg.sterms();
            if (terms == null || terms.buckets() == null || terms.buckets().array() == null) {
                return Collections.emptyList();
            }
            List<SrvExceptionSummary> list = new ArrayList<>();
            for (var b : terms.buckets().array()) {
                SrvExceptionSummary s = new SrvExceptionSummary();
                var k = b.key();
                String methodKey = k == null ? null : (k.isString() ? k.stringValue() : k._toJsonString());
                s.setMethodKey(methodKey);
                s.setCallCount(b.docCount());
                Aggregate lastCallAgg = b.aggregations() == null ? null : b.aggregations().get("last_call_ts");
                Double lastCallVal = lastCallAgg != null && lastCallAgg.max() != null ? lastCallAgg.max().value() : null;
                s.setLastCallTime(lastCallVal == null ? 0L : lastCallVal.longValue());

                Aggregate excAgg = b.aggregations() == null ? null : b.aggregations().get("exception_filter");
                if (excAgg != null && excAgg.filter() != null) {
                    s.setExceptionCount(excAgg.filter().docCount());
                    Aggregate lastExcAgg = excAgg.filter().aggregations() == null ? null : excAgg.filter().aggregations().get("last_exception_ts");
                    Double lastExcVal = lastExcAgg != null && lastExcAgg.max() != null ? lastExcAgg.max().value() : null;
                    s.setLastExceptionTime(lastExcVal == null ? 0L : lastExcVal.longValue());
                } else {
                    s.setExceptionCount(0);
                    s.setLastExceptionTime(0L);
                }
                list.add(s);
            }
            list.removeIf(x -> x.getExceptionCount() <= 0);
            list.sort(Comparator.comparingLong(SrvExceptionSummary::getLastExceptionTime).reversed()
                    .thenComparingLong(SrvExceptionSummary::getExceptionCount).reversed());
            if (list.size() > topN) {
                return new ArrayList<>(list.subList(0, topN));
            }
            return list;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private Query buildRecentSummaryQuery(long startTime) {
        return Query.of(q -> q.bool(b -> b
                .filter(f -> f.range(r -> r.field("timestamp").gte(JsonData.of(startTime))))
                .filter(f -> f.exists(e -> e.field("methodKey")))
        ));
    }

    private Query buildExceptionQuery(String methodKey, Long startTime, Long endTime) {
        return Query.of(q -> q.bool(b -> {
            b.filter(f -> f.term(t -> t.field("success").value(false)));
            if (methodKey != null && !methodKey.isBlank()) {
                b.filter(f -> f.bool(bb -> bb
                        .should(s -> s.term(t -> t.field("methodKey.keyword").value(methodKey)))
                        .should(s -> s.term(t -> t.field("methodKey").value(methodKey)))
                        .minimumShouldMatch("1")
                ));
            }
            if (startTime != null || endTime != null) {
                b.filter(f -> f.range(r -> {
                    r.field("timestamp");
                    if (startTime != null) {
                        r.gte(JsonData.of(startTime));
                    }
                    if (endTime != null) {
                        r.lte(JsonData.of(endTime));
                    }
                    return r;
                }));
            }
            return b;
        }));
    }
}

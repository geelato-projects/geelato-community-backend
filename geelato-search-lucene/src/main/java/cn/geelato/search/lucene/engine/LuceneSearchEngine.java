package cn.geelato.search.lucene.engine;

import cn.geelato.search.api.SearchDocument;
import cn.geelato.search.api.SearchEngine;
import cn.geelato.search.api.SearchEngineCapabilities;
import cn.geelato.search.api.SearchEngineHealth;
import cn.geelato.search.api.SearchQuery;
import cn.geelato.search.api.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.core.LowerCaseFilterFactory;
import org.apache.lucene.analysis.ngram.NGramTokenizerFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.queries.spans.SpanNearQuery;
import org.apache.lucene.queries.spans.SpanQuery;
import org.apache.lucene.queries.spans.SpanTermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 嵌入式 Lucene 检索引擎。
 *
 * <p><b>contains 语义实现</b>：编号字段经 NGramTokenizer(2,2) 建倒排（token 位置 = gram 起点序号，
 * 相邻 token 起点差 1 即字符连续）；检索时关键词拆为滑动 2-gram 序列，以
 * SpanNearQuery(slop=0, inOrder) 要求各 gram 按序紧邻，等价于精确连续子串匹配——
 * 多关键词 OR × 多字段 OR 叠加过滤字段，与 gfn_fuzzymatch 的多词 REGEXP 语义对齐
 * （大小写不敏感：索引与查询统一小写，对齐平台 MySQL 默认 *_ci collation）。
 * 长度 1 的关键词无对应 2-gram，由路由侧回退 SQL，引擎不做保证。
 *
 * <p><b>NRT</b>：每域一个 IndexWriter + SearcherManager，写后置脏标记，检索前
 * maybeRefreshBlocking；close 时统一提交关闭。reindexed 水位以域目录旁的 marker 文件
 * 记录，未标记 reindexed 的域不参与路由（防止存量数据未入库时路由漏单）。
 */
@Slf4j
public class LuceneSearchEngine implements SearchEngine, AutoCloseable {

    public static final String F_ID = "__id";
    public static final String F_TENANT = "__tenant";
    public static final String F_DEL_STATUS = "__del_status";
    public static final String F_APP = "__app";

    private final Path rootDir;
    private final int maxIdsLimit;
    private final Analyzer analyzer;
    private final Map<String, DomainIndex> domainIndexes = new ConcurrentHashMap<>();
    private volatile boolean closed = false;

    public LuceneSearchEngine(String indexRootDir, int maxIdsLimit) {
        this.rootDir = Path.of(indexRootDir);
        this.maxIdsLimit = maxIdsLimit;
        try {
            this.analyzer = CustomAnalyzer.builder()
                    .withTokenizer(NGramTokenizerFactory.class, "minGramSize", "2", "maxGramSize", "2")
                    .addTokenFilter(LowerCaseFilterFactory.class)
                    .build();
        } catch (IOException e) {
            throw new IllegalStateException("初始化 ngram 分析器失败", e);
        }
    }

    @Override
    public String name() {
        return "lucene";
    }

    @Override
    public SearchEngineCapabilities capabilities() {
        return new SearchEngineCapabilities(
                SearchEngineCapabilities.Consistency.SYNC_FAIL_FAST, true, maxIdsLimit);
    }

    @Override
    public void upsert(String domainId, List<SearchDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        DomainIndex index = domainIndex(domainId);
        try {
            for (SearchDocument doc : documents) {
                if (doc == null || doc.getId() == null) {
                    continue;
                }
                index.writer.updateDocument(new Term(F_ID, doc.getId()), toLuceneDocument(doc));
            }
            // 显式提交：updateDocument 只写内存 RAM buffer（NRT reader 可见但未持久化），
            // 不 commit 则段文件仅在 buffer 满(默认16MB)或 close 时落盘——进程重启即丢失。
            // 单据业务写入 QPS 低，每次调用（事件单文档/批量/reindex 每批）直接 commit，
            // fsync 毫秒级且在异步事件线程执行，不影响业务；吞吐敏感场景再引入阈值批量提交。
            index.writer.commit();
            index.dirty = true;
        } catch (IOException e) {
            throw new IllegalStateException("索引写入失败: domain=" + domainId, e);
        }
    }

    @Override
    public void delete(String domainId, Collection<String> docIds) {
        if (docIds == null || docIds.isEmpty()) {
            return;
        }
        DomainIndex index = domainIndex(domainId);
        List<Term> terms = new ArrayList<>();
        for (String id : docIds) {
            if (id != null) {
                terms.add(new Term(F_ID, id));
            }
        }
        if (terms.isEmpty()) {
            return;
        }
        try {
            index.writer.deleteDocuments(terms.toArray(new Term[0]));
            index.writer.commit();
            index.dirty = true;
        } catch (IOException e) {
            throw new IllegalStateException("索引删除失败: domain=" + domainId, e);
        }
    }

    @Override
    public SearchResult search(String domainId, SearchQuery query) {
        if (query == null || query.getTerms() == null || query.getTerms().isEmpty()
                || query.getFields() == null || query.getFields().isEmpty()) {
            return SearchResult.empty();
        }
        DomainIndex index = domainIndex(domainId);
        try {
            index.refreshIfDirty();
            IndexSearcher searcher = index.searcherManager.acquire();
            try {
                BooleanQuery.Builder match = new BooleanQuery.Builder();
                boolean anyClause = false;
                for (String term : query.getTerms()) {
                    if (term == null || term.isEmpty()) {
                        continue;
                    }
                    for (String field : query.getFields()) {
                        SpanQuery q = buildContainsQuery(field, term);
                        if (q != null) {
                            match.add(q, BooleanClause.Occur.SHOULD);
                            anyClause = true;
                        }
                    }
                }
                if (!anyClause) {
                    return SearchResult.empty();
                }
                BooleanQuery.Builder full = new BooleanQuery.Builder();
                full.add(match.build(), BooleanClause.Occur.MUST);
                addFilter(full, F_TENANT, query.getTenantCode());
                addFilter(full, F_DEL_STATUS, query.getDelStatus());
                addFilter(full, F_APP, query.getAppId());

                int limit = Math.min(query.getMaxIds() <= 0 ? maxIdsLimit : query.getMaxIds(), maxIdsLimit);
                TopDocs topDocs = searcher.search(full.build(), limit + 1);
                boolean truncated = topDocs.scoreDocs.length > limit;
                List<String> ids = new ArrayList<>(Math.min(topDocs.scoreDocs.length, limit));
                for (int i = 0; i < Math.min(topDocs.scoreDocs.length, limit); i++) {
                    ScoreDoc sd = topDocs.scoreDocs[i];
                    ids.add(searcher.storedFields().document(sd.doc).get(F_ID));
                }
                return new SearchResult(ids, truncated);
            } finally {
                index.searcherManager.release(searcher);
            }
        } catch (IOException e) {
            throw new IllegalStateException("索引检索失败: domain=" + domainId, e);
        }
    }

    @Override
    public SearchEngineHealth health() {
        if (closed) {
            return SearchEngineHealth.down("engine closed");
        }
        return SearchEngineHealth.up("domains=" + domainIndexes.size() + ", root=" + rootDir);
    }

    /** 域是否已完成存量重建（未重建的域不参与路由）。 */
    public boolean hasReindexed(String domainId) {
        return Files.exists(reindexMarker(rootDir, domainId));
    }

    /** 标记域完成存量重建。 */
    public void markReindexed(String domainId) {
        try {
            Files.createDirectories(rootDir);
            Files.writeString(reindexMarker(rootDir, domainId),
                    String.valueOf(System.currentTimeMillis()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("写入 reindex 水位失败: domain=" + domainId, e);
        }
    }

    /** 当前索引文档数（对账用；域未初始化返回 0）。 */
    public long countDocuments(String domainId) {
        DomainIndex index = domainIndexes.get(domainId);
        if (index == null) {
            return 0;
        }
        try {
            index.refreshIfDirty();
            IndexSearcher searcher = index.searcherManager.acquire();
            try {
                return searcher.count(new MatchAllDocsQuery());
            } finally {
                index.searcherManager.release(searcher);
            }
        } catch (IOException e) {
            throw new IllegalStateException("索引计数失败: domain=" + domainId, e);
        }
    }

    @Override
    public void close() {
        closed = true;
        domainIndexes.forEach((domainId, index) -> {
            try {
                index.close();
            } catch (IOException e) {
                log.error("关闭域索引失败: domain={}", domainId, e);
            }
        });
        domainIndexes.clear();
    }

    // ===== 内部 =====

    private static Path reindexMarker(Path root, String domainId) {
        return root.resolve(domainId + ".reindexed");
    }

    private static void addFilter(BooleanQuery.Builder builder, String field, String value) {
        if (value != null) {
            builder.add(new TermQuery(new Term(field, value)), BooleanClause.Occur.FILTER);
        }
    }

    /**
     * 关键词 → 精确连续子串查询：长度 2 直接 TermQuery；更长用滑动 2-gram 的
     * SpanNear(slop=0, inOrder)——相邻 token 起点差 1 即字符连续。
     */
    private SpanQuery buildContainsQuery(String field, String term) {
        String lower = term.toLowerCase();
        if (lower.length() < 2) {
            return new SpanTermQuery(new Term(field, lower));
        }
        if (lower.length() == 2) {
            return new SpanTermQuery(new Term(field, lower));
        }
        List<SpanQuery> grams = new ArrayList<>();
        for (int i = 0; i + 2 <= lower.length(); i++) {
            grams.add(new SpanTermQuery(new Term(field, lower.substring(i, i + 2))));
        }
        return new SpanNearQuery(grams.toArray(new SpanQuery[0]), 0, true);
    }

    private Document toLuceneDocument(SearchDocument doc) {
        Document d = new Document();
        // Store.YES：编号/过滤字段值极短，存储开销可忽略，换取调试端点可窥视文档实际值
        d.add(new StringField(F_ID, doc.getId(), Field.Store.YES));
        if (doc.getTenantCode() != null) {
            d.add(new StringField(F_TENANT, doc.getTenantCode(), Field.Store.YES));
        }
        if (doc.getDelStatus() != null) {
            d.add(new StringField(F_DEL_STATUS, doc.getDelStatus(), Field.Store.YES));
        }
        if (doc.getAppId() != null) {
            d.add(new StringField(F_APP, doc.getAppId(), Field.Store.YES));
        }
        for (Map.Entry<String, List<String>> e : doc.getFieldValues().entrySet()) {
            for (String value : e.getValue()) {
                if (value != null && !value.isEmpty()) {
                    d.add(new TextField(e.getKey(), value, Field.Store.YES));
                }
            }
        }
        return d;
    }

    /**
     * 窥视索引文档的实际存储值（调试端点用）：返回 字段名 → 值列表
     * （多值字段含多个值）；文档不存在返回 null。需要字段以 Store.YES 写入
     * （2026-09-14 起编号/过滤字段均存储；此前构建的索引不含存储值，重建后可窥视）。
     */
    public Map<String, List<String>> peekDocument(String domainId, String docId) {
        DomainIndex index = domainIndexes.get(domainId);
        if (index == null) {
            return null;
        }
        try {
            index.refreshIfDirty();
            IndexSearcher searcher = index.searcherManager.acquire();
            try {
                TopDocs docs = searcher.search(new TermQuery(new Term(F_ID, docId)), 1);
                if (docs.scoreDocs.length == 0) {
                    return null;
                }
                Document d = searcher.storedFields().document(docs.scoreDocs[0].doc);
                Map<String, List<String>> result = new java.util.LinkedHashMap<>();
                for (IndexableField f : d.getFields()) {
                    String value = f.stringValue();
                    if (value != null) {
                        result.computeIfAbsent(f.name(), k -> new ArrayList<>()).add(value);
                    }
                }
                return result;
            } finally {
                index.searcherManager.release(searcher);
            }
        } catch (IOException e) {
            throw new IllegalStateException("窥视索引文档失败: domain=" + domainId + ", docId=" + docId, e);
        }
    }

    private DomainIndex domainIndex(String domainId) {
        return domainIndexes.computeIfAbsent(domainId, id -> {
            try {
                Files.createDirectories(rootDir);
                Path dir = rootDir.resolve(id);
                Directory directory = FSDirectory.open(dir);
                IndexWriterConfig config = new IndexWriterConfig(analyzer);
                config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
                IndexWriter writer = new IndexWriter(directory, config);
                SearcherManager searcherManager = new SearcherManager(writer, false, false, null);
                return new DomainIndex(directory, writer, searcherManager);
            } catch (IOException e) {
                throw new IllegalStateException("打开域索引失败: domain=" + id + ", root=" + rootDir, e);
            }
        });
    }

    /** 单域的 writer/searcher/脏标记。 */
    static final class DomainIndex {
        final Directory directory;
        final IndexWriter writer;
        final SearcherManager searcherManager;
        volatile boolean dirty = false;

        DomainIndex(Directory directory, IndexWriter writer, SearcherManager searcherManager) {
            this.directory = directory;
            this.writer = writer;
            this.searcherManager = searcherManager;
        }

        synchronized void refreshIfDirty() throws IOException {
            if (dirty) {
                searcherManager.maybeRefreshBlocking();
                dirty = false;
            }
        }

        void close() throws IOException {
            IOException first = null;
            try {
                searcherManager.close();
            } catch (IOException e) {
                first = e;
            }
            try {
                writer.close();
            } catch (IOException e) {
                if (first == null) {
                    first = e;
                }
            }
            try {
                directory.close();
            } catch (IOException e) {
                if (first == null) {
                    first = e;
                }
            }
            if (first != null) {
                throw first;
            }
        }
    }
}

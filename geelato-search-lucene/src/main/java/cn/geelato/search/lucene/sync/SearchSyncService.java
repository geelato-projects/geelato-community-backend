package cn.geelato.search.lucene.sync;

import cn.geelato.search.api.SearchDocument;
import cn.geelato.search.api.SearchEngine;
import cn.geelato.search.lucene.config.GeelatoSearchProperties.DomainConfig;
import cn.geelato.search.lucene.engine.LuceneSearchEngine;

import java.util.List;
import java.util.Map;

/**
 * 索引同步核心逻辑：监听器、补偿任务、对账任务、reindex 共用。
 * 统一以"回表重建整文档"为同步单元，幂等、乱序安全。
 */
public class SearchSyncService {

    private final SearchEngine searchEngine;
    private final DocumentLoader documentLoader;

    public SearchSyncService(SearchEngine searchEngine, DocumentLoader documentLoader) {
        this.searchEngine = searchEngine;
        this.documentLoader = documentLoader;
    }

    /**
     * 重建并写入主文档。
     *
     * @throws SyncMissException 主行当前不可读（未提交竞态/已物理删除）——可重试场景，
     *                           调用方记补偿延后收敛
     */
    public void syncUpsert(DomainConfig domain, String docId) {
        SearchDocument doc = documentLoader.load(domain, docId);
        if (doc == null) {
            throw new SyncMissException("主行不可读: domain=" + domain.getId() + ", docId=" + docId);
        }
        searchEngine.upsert(domain.getId(), java.util.List.of(doc));
    }

    public void syncDelete(DomainConfig domain, String docId) {
        searchEngine.delete(domain.getId(), java.util.List.of(docId));
    }

    /**
     * 批量重建写入（全量重建/对账用）：主行与子实体各一次 IN 批查，往返 O(批数)。
     *
     * @return 成功写入的文档数（主行缺失的跳过，不计入）
     */
    public int syncUpsertBatch(DomainConfig domain, List<String> docIds) {
        if (docIds == null || docIds.isEmpty()) {
            return 0;
        }
        Map<String, SearchDocument> docs = documentLoader.loadBatch(domain, docIds);
        if (!docs.isEmpty()) {
            searchEngine.upsert(domain.getId(), new java.util.ArrayList<>(docs.values()));
        }
        return docs.size();
    }

    public DocumentLoader getDocumentLoader() {
        return documentLoader;
    }

    public LuceneSearchEngine getLuceneEngine() {
        return (LuceneSearchEngine) searchEngine;
    }

    /** 行缺失/竞态类可重试失败。 */
    public static class SyncMissException extends RuntimeException {
        public SyncMissException(String message) {
            super(message);
        }
    }
}

package cn.geelato.search.lucene.engine;

import cn.geelato.search.api.SearchDocument;
import cn.geelato.search.api.SearchQuery;
import cn.geelato.search.api.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 索引持久化回归测试：upsert/delete 必须显式 commit——
 * 只写不 commit 时数据仅在内存 RAM buffer（NRT reader 可见但磁盘段文件为空），
 * 进程重启即全部丢失（曾出现的"索引文件全是 0KB"问题）。
 */
class LuceneSearchEnginePersistenceTest {

    private static final String ROOT = "target/search-persistence-test";

    private static SearchQuery query(String term) {
        SearchQuery q = new SearchQuery();
        q.setTerms(List.of(term));
        q.setFields(List.of("soNo"));
        return q;
    }

    @Test
    void upsertedDocumentsSurviveEngineRestart() throws Exception {
        SearchDocument doc = new SearchDocument("d1");
        doc.addFieldValue("soNo", "SO274935136");

        LuceneSearchEngine first = new LuceneSearchEngine(ROOT, 50000);
        first.upsert("order", List.of(doc));
        // 模拟进程结束：close（隐含最后一次 commit）
        first.close();

        LuceneSearchEngine reopened = new LuceneSearchEngine(ROOT, 50000);
        try {
            assertEquals(List.of("d1"), reopened.search("order", query("274935136")).getIds());
        } finally {
            reopened.close();
        }
    }

    @Test
    void committedWithoutCloseSurvivesHardRestart() throws Exception {
        // 更严苛场景：写完未 close（进程被杀），已 commit 的段文件在磁盘上非空、可被后续重开读取
        LuceneSearchEngine writer = new LuceneSearchEngine(ROOT, 50000);
        SearchDocument doc = new SearchDocument("d2");
        doc.addFieldValue("soNo", "SO999888777");
        writer.upsert("order", List.of(doc));
        // 不 close，直接弃置引用（等价进程被杀；write.lock 随 JVM 退出释放，同 JVM 内无法重开验证）
        // 落盘断言：域目录下的段提交点已持久化且非空（Lucene 提交代次为 36 进制，如 segments_g）
        java.nio.file.Path segDir = java.nio.file.Path.of(ROOT, "order");
        try (var files = java.nio.file.Files.list(segDir)) {
            boolean hasNonEmptySegment = files
                    .filter(p -> p.getFileName().toString().matches("segments_[a-z0-9]+"))
                    .anyMatch(p -> p.toFile().length() > 0);
            assertTrue(hasNonEmptySegment, "段提交点应已持久化且非空（0KB 即未 commit 回归）");
        }
    }

    @Test
    void deletedDocumentsStayDeletedAfterRestart() throws Exception {
        LuceneSearchEngine first = new LuceneSearchEngine(ROOT, 50000);
        SearchDocument doc = new SearchDocument("d3");
        doc.addFieldValue("soNo", "SO111222333");
        first.upsert("order", List.of(doc));
        first.delete("order", List.of("d3"));
        first.close();

        LuceneSearchEngine reopened = new LuceneSearchEngine(ROOT, 50000);
        try {
            SearchResult result = reopened.search("order", query("SO111222333"));
            assertTrue(result.getIds().isEmpty());
        } finally {
            reopened.close();
        }
    }
}

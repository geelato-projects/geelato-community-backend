package cn.geelato.search.lucene.engine;

import cn.geelato.search.api.SearchEngine;
import cn.geelato.search.api.tck.SearchEngineContractTest;

import java.nio.file.Files;

/**
 * Lucene 实现的 TCK 契约测试：语义与实现解耦的保障，
 * 后续新增 ES 实现时执行同一套契约，保证检索语义不漂移。
 */
class LuceneSearchEngineContractTest extends SearchEngineContractTest {

    private static final String ROOT = "target/search-index-test";

    @Override
    protected SearchEngine createEngine() throws Exception {
        return new LuceneSearchEngine(ROOT, 50000);
    }

    @Override
    protected void destroyEngine(SearchEngine engine) throws Exception {
        ((LuceneSearchEngine) engine).close();
        // 清理测试目录，保证用例独立
        try (var walk = Files.walk(java.nio.file.Path.of(ROOT))) {
            walk.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                        }
                    });
        }
    }
}

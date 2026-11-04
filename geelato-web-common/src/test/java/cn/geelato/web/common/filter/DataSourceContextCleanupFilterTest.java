package cn.geelato.web.common.filter;

import cn.geelato.datasource.DataSourceOverrideContext;
import cn.geelato.datasource.DynamicDataSourceHolder;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNull;

class DataSourceContextCleanupFilterTest {

    private final DataSourceContextCleanupFilter filter = new DataSourceContextCleanupFilter();

    @AfterEach
    void cleanUp() {
        DynamicDataSourceHolder.clearDataSourceKey();
        DataSourceOverrideContext.clear();
    }

    @Test
    void clearsLeakedRoutingKeyAfterRequest() throws Exception {
        DynamicDataSourceHolder.setDataSourceKey("leaked-key");
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (request, response) -> {
                });
        assertNull(DynamicDataSourceHolder.getDataSourceKey());
    }

    @Test
    void clearsOverrideContextAfterRequest() throws Exception {
        DataSourceOverrideContext.set("archive", true);
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (request, response) -> {
                });
        assertNull(DataSourceOverrideContext.current());
    }

    @Test
    void clearsEvenWhenChainThrows() {
        DynamicDataSourceHolder.setDataSourceKey("leaked-key");
        try {
            filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                    (request, response) -> {
                        throw new ServletException("boom");
                    });
        } catch (Exception expected) {
            // 链路异常也应完成清理
        }
        assertNull(DynamicDataSourceHolder.getDataSourceKey());
    }
}

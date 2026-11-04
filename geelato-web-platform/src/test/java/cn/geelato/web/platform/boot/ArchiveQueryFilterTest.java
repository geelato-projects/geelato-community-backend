package cn.geelato.web.platform.boot;

import cn.geelato.datasource.DataSourceOverrideContext;
import cn.geelato.datasource.DynamicDataSourceRegistry;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchiveQueryFilterTest {

    private final DynamicDataSourceRegistry registry = new DynamicDataSourceRegistry(null, null, null, null, null);
    private final ArchiveQueryFilter filter = new ArchiveQueryFilter(registry);

    @AfterEach
    void cleanUp() {
        DataSourceOverrideContext.clear();
        registry.removeDataSource(ArchiveQueryAutoConfiguration.ARCHIVE_DATASOURCE_KEY);
    }

    @Test
    void noHeaderLeavesOverrideUnset() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertNull(DataSourceOverrideContext.current());
    }

    @Test
    void headerEnablesReadOnlyOverrideWhenConfigured() throws Exception {
        registry.registerDataSource(ArchiveQueryAutoConfiguration.ARCHIVE_DATASOURCE_KEY, new HikariDataSource());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ArchiveQueryFilter.HEADER_NAME, "true");
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertNull(DataSourceOverrideContext.current());
    }

    @Test
    void overrideActiveOnlyWithinRequestScope() throws Exception {
        registry.registerDataSource(ArchiveQueryAutoConfiguration.ARCHIVE_DATASOURCE_KEY, new HikariDataSource());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ArchiveQueryFilter.HEADER_NAME, "true");
        MockCaptureChain chain = new MockCaptureChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertTrue(chain.reached);
        assertNull(DataSourceOverrideContext.current());
    }

    @Test
    void acceptedHeaderValuesAreCaseInsensitiveVariants() throws Exception {
        registry.registerDataSource(ArchiveQueryAutoConfiguration.ARCHIVE_DATASOURCE_KEY, new HikariDataSource());
        for (String value : new String[]{"TRUE", "1", "on", " true "}) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(ArchiveQueryFilter.HEADER_NAME, value);
            MockCaptureChain chain = new MockCaptureChain();
            filter.doFilter(request, new MockHttpServletResponse(), chain);
            assertTrue(chain.reached, "值应被接受: " + value);
        }
        assertNull(DataSourceOverrideContext.current());
    }

    @Test
    void headerWithoutConfiguredArchiveDataSourceHardFails() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ArchiveQueryFilter.HEADER_NAME, "true");
        ServletException ex = assertThrows(ServletException.class,
                () -> filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain()));
        assertTrue(ex.getMessage().contains("spring.datasource.archive.jdbc-url"));
        assertNull(DataSourceOverrideContext.current());
    }

    @Test
    void invalidHeaderValueHardFails() {
        registry.registerDataSource(ArchiveQueryAutoConfiguration.ARCHIVE_DATASOURCE_KEY, new HikariDataSource());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ArchiveQueryFilter.HEADER_NAME, "yes-please");
        ServletException ex = assertThrows(ServletException.class,
                () -> filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain()));
        assertTrue(ex.getMessage().contains("仅接受 true/1/on"));
        assertNull(DataSourceOverrideContext.current());
    }

    /**
     * 捕获过滤器链是否到达（即请求未被拦截中断），并记录链内时刻的覆盖状态。
     */
    static class MockCaptureChain extends MockFilterChain {
        boolean reached;
        DataSourceOverrideContext.DataSourceOverride overrideAtChain;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
                throws IOException, ServletException {
            this.reached = true;
            this.overrideAtChain = DataSourceOverrideContext.current();
            super.doFilter(request, response);
        }
    }

    @Test
    void overrideKeyIsArchiveReadOnlyWithinChain() throws Exception {
        registry.registerDataSource(ArchiveQueryAutoConfiguration.ARCHIVE_DATASOURCE_KEY, new HikariDataSource());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ArchiveQueryFilter.HEADER_NAME, "true");
        MockCaptureChain chain = new MockCaptureChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertEquals("archive", chain.overrideAtChain.connectId());
        assertTrue(chain.overrideAtChain.readOnly());
    }
}

package cn.geelato.web.platform.boot;

import cn.geelato.datasource.DataSourceOverrideContext;
import cn.geelato.datasource.DynamicDataSourceRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 归档查询模式入口：解析 {@code X-Gl-Archive-Query} header（值 true/1/on，不区分大小写），
 * 开启请求级只读数据源覆盖（路由到 "archive" 键），请求结束 finally 清理。
 * <p>header 存在但归档数据源未配置（缺 spring.datasource.archive.jdbc-url）时硬失败，
 * 拒绝静默降级到主库造成"以为在看归档、实际看的是主库"的假象。
 */
public class ArchiveQueryFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Gl-Archive-Query";

    private static final Logger log = LoggerFactory.getLogger(ArchiveQueryFilter.class);

    private final DynamicDataSourceRegistry registry;

    public ArchiveQueryFilter(DynamicDataSourceRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HEADER_NAME);
        try {
            if (header != null && !header.isBlank()) {
                String value = header.trim();
                if (!"true".equalsIgnoreCase(value) && !"1".equals(value) && !"on".equalsIgnoreCase(value)) {
                    throw new ServletException(String.format(
                            "无效的归档查询请求头 %s: %s（仅接受 true/1/on）", HEADER_NAME, value));
                }
                if (!registry.containsDataSource(ArchiveQueryAutoConfiguration.ARCHIVE_DATASOURCE_KEY)) {
                    throw new ServletException(String.format(
                            "已请求归档查询模式（%s header），但未配置归档数据源：请在 application.properties 配置 spring.datasource.archive.jdbc-url 等连接信息",
                            HEADER_NAME));
                }
                DataSourceOverrideContext.set(ArchiveQueryAutoConfiguration.ARCHIVE_DATASOURCE_KEY, true);
                log.debug("归档查询模式已开启，本请求无归属实体路由到: {}",
                        ArchiveQueryAutoConfiguration.ARCHIVE_DATASOURCE_KEY);
            }
            filterChain.doFilter(request, response);
        } finally {
            DataSourceOverrideContext.clear();
        }
    }
}

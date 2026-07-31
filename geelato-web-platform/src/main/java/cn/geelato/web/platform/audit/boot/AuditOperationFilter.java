package cn.geelato.web.platform.audit.boot;

import cn.geelato.web.platform.audit.context.AuditContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 通道A + 请求级上下文生命周期过滤器。
 *
 * <p>职责：
 * <ol>
 *   <li>为每个请求生成 requestId 并初始化 {@link AuditContext}；</li>
 *   <li>读取请求头 {@code X-Audit-Operation} / {@code X-Audit-Biz-Type}，声明业务意图，
 *       使 MQL/通用 CRUD 接口也能带上业务语义；</li>
 *   <li>请求结束时清理 {@link AuditContext}，防止 ThreadLocal 泄漏。</li>
 * </ol>
 *
 * <p>声明意图属于"尽力而为"：未声明时由第2层 ORM 兜底中文化，不影响记录可读性。
 */
public class AuditOperationFilter extends OncePerRequestFilter {

    public static final String HDR_OPERATION = "X-Audit-Operation";
    public static final String HDR_BIZ_TYPE = "X-Audit-Biz-Type";
    public static final String HDR_TARGET_ID = "X-Audit-Target-Id";
    public static final String HDR_TRACE_ID = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        AuditContext ctx = AuditContext.current();
        try {
            // requestId（traceId 由 AuditContextProvider 从请求头统一采集）
            String requestId = UUID.randomUUID().toString().replace("-", "");
            ctx.setRequestId(requestId);

            // 通道A：读取声明的业务意图
            String operName = request.getHeader(HDR_OPERATION);
            String bizType = request.getHeader(HDR_BIZ_TYPE);
            String targetId = request.getHeader(HDR_TARGET_ID);
            if (operName != null || bizType != null || targetId != null) {
                ctx.declare(operName, bizType, targetId);
            }

            filterChain.doFilter(request, response);
        } finally {
            AuditContext.clear();
        }
    }
}

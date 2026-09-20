package cn.geelato.web.common.filter;

import cn.geelato.core.experiment.ExperimentContext;
import cn.geelato.core.experiment.ExperimentFeatures;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 实验功能请求级开关入口：解析 {@code X-Gl-Experiments} header
 * （逗号分隔功能名，容忍空白与大小写），填充 {@link ExperimentContext}，
 * 请求结束 finally 清理。
 *
 * <p>只承载"本请求声明开启的功能名"；构建期白名单与合并语义见
 * {@link cn.geelato.core.experiment.ExperimentGate}（规范权威定义）。
 * 白名单外的功能名在此记 debug 并丢弃（header 传了也不生效，便于排查）。
 */
public class ExperimentFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Gl-Experiments";

    private static final Logger log = LoggerFactory.getLogger(ExperimentFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HEADER_NAME);
        try {
            if (header != null && !header.isBlank()) {
                List<String> declared = Arrays.stream(header.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
                List<String> allowed = declared.stream()
                        .filter(f -> ExperimentFeatures.allowed().contains(f.toLowerCase()))
                        .toList();
                if (declared.size() != allowed.size() && log.isDebugEnabled()) {
                    List<String> dropped = declared.stream()
                            .filter(f -> !ExperimentFeatures.allowed().contains(f.toLowerCase()))
                            .toList();
                    log.debug("实验功能 header 含未放行功能名(构建期白名单外,已忽略): {} -> {}",
                            dropped, ExperimentFeatures.allowed());
                }
                ExperimentContext.enable(allowed);
            }
            filterChain.doFilter(request, response);
        } finally {
            ExperimentContext.clear();
        }
    }
}

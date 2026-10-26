package cn.geelato.ide.debug;

import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 自托管 chrome-devtools-frontend 静态资源装配（同源，可连本模块 ws 代理）。
 * <p>
 * 输出于 {@code ${geelato.ide.debug.devtoolsBase}/**}（缺省 {@code /debug-devtools/**}）。资源位置二选一：
 * <ul>
 *   <li>{@code geelato.ide.debug.devtoolsDir} 非空 → {@code file:<dir>/}（部署外挂巨型前端包，推荐）；</li>
 *   <li>否则回落 {@code classpath:/static/debug-devtools/}（随包内置）。</li>
 * </ul>
 * {@code chrome-devtools-frontend}（{@code devtools-frontend}/{@code js_app.html} 及其 {@code *.js}）体积巨大、
 * 不随本仓打包，须由部署方放置到上述目录；缺失时 iframe 加载 404，前端调试面板有兜底提示。
 *
 * @author geelato
 */
@Configuration
public class DebugDevToolsResourceConfig implements WebMvcConfigurer {

    @Value("${geelato.ide.debug.devtoolsBase:/debug-devtools}")
    private String devtoolsBase;

    @Value("${geelato.ide.debug.devtoolsDir:}")
    private String devtoolsDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String pattern = normalizeBase(devtoolsBase) + "/**";
        String location = Strings.isNotBlank(devtoolsDir)
                ? "file:" + ensureTrailingSlash(devtoolsDir)
                : "classpath:/static/debug-devtools/";
        registry.addResourceHandler(pattern)
                .addResourceLocations(location)
                .setCachePeriod(3600);
    }

    private static String normalizeBase(String base) {
        String b = Strings.isBlank(base) ? "/debug-devtools" : base.trim();
        if (!b.startsWith("/")) {
            b = "/" + b;
        }
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        return b;
    }

    private static String ensureTrailingSlash(String dir) {
        String d = dir.trim();
        return d.endsWith("/") || d.endsWith("\\") ? d : d + "/";
    }
}

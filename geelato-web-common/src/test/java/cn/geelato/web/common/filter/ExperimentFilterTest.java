package cn.geelato.web.common.filter;

import cn.geelato.core.experiment.ExperimentContext;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ExperimentFilter 测试：X-Gl-Experiments 解析、白名单过滤与请求结束清理。
 * 断言在过滤链内部（业务处理期间）捕获——Filter finally 会在请求结束清理上下文。
 */
class ExperimentFilterTest {

    private final ExperimentFilter filter = new ExperimentFilter();

    @AfterEach
    void clearContext() {
        ExperimentContext.clear();
    }

    /** 执行 Filter，返回链内（业务处理期间）观察到的实验上下文快照。 */
    @SuppressWarnings("unchecked")
    private Set<String> captureDuringRequest(String header) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (header != null) {
            request.addHeader(ExperimentFilter.HEADER_NAME, header);
        }
        final Set<String>[] captured = new Set[1];
        MockFilterChain chain = new MockFilterChain(new HttpServlet() {
            @Override
            public void service(HttpServletRequest req, HttpServletResponse res) {
                captured[0] = ExperimentContext.current();
            }
        });
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return captured[0] == null ? Set.of() : captured[0];
    }

    @Test
    void parsesSingleFeature() throws Exception {
        assertEquals(Set.of("search"), captureDuringRequest("search"));
    }

    @Test
    void parsesMultipleFeaturesAndToleratesWhitespaceAndCase() throws Exception {
        // 多值 + 空白 + 大小写容忍；another-allowed 不在构建期白名单被丢弃（与 drops 用例语义互补）
        assertEquals(Set.of("search"), captureDuringRequest("  SEARCH , Another-Allowed ,, "));
    }

    @Test
    void dropsFeaturesOutsideBuildTimeWhitelist() throws Exception {
        // 白名单外功能名被 Filter 丢弃，不进上下文（header 传了也不生效）
        assertEquals(Set.of("search"), captureDuringRequest("not-registered,search"));
    }

    @Test
    void noOrBlankHeaderLeavesContextEmpty() throws Exception {
        assertTrue(captureDuringRequest(null).isEmpty());
        assertTrue(captureDuringRequest("  ").isEmpty());
        assertTrue(captureDuringRequest(",,,").isEmpty());
    }

    @Test
    void contextClearedAfterRequest() throws Exception {
        captureDuringRequest("search");
        assertTrue(ExperimentContext.current().isEmpty(),
                "请求结束后 ExperimentContext 必须被清理（防线程复用串号）");
    }

    @Test
    void gateReflectsHeaderDuringRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ExperimentFilter.HEADER_NAME, "search");
        final boolean[] gateResult = new boolean[1];
        MockFilterChain chain = new MockFilterChain(new HttpServlet() {
            @Override
            public void service(HttpServletRequest req, HttpServletResponse res) {
                gateResult[0] = cn.geelato.core.experiment.ExperimentGate
                        .isEnabled(cn.geelato.core.experiment.ExperimentFeatures.SEARCH);
            }
        });
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertTrue(gateResult[0], "链内 ExperimentGate 应生效");
        assertFalse(cn.geelato.core.experiment.ExperimentGate
                        .isEnabled(cn.geelato.core.experiment.ExperimentFeatures.SEARCH),
                "链外（请求结束）应回退为关");
    }
}

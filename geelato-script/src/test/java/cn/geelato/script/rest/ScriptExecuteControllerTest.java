package cn.geelato.script.rest;

import cn.geelato.script.core.DefaultScriptEngine;
import cn.geelato.script.core.EngineRevision;
import cn.geelato.script.core.ScriptEngineBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ScriptExecuteControllerTest {

    private static final DefaultScriptEngine engine = new ScriptEngineBuilder()
            .revision(new EngineRevision("v0", EngineRevision.Status.DEPRECATED, "请迁移到 v1", "2022",
                    Map.of(), Map.of(), Map.of()))
            .revision(new EngineRevision("vEOL", EngineRevision.Status.RETIRED, "已下线", "2022",
                    Map.of(), Map.of(), Map.of()))
            .defaultVersion("v1")
            .build();
    private static final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ScriptExecuteController(engine)).build();

    @AfterAll
    static void shutdown() {
        engine.close();
    }

    @Test
    void executeRoutesByEngineVersion() throws Exception {
        mvc.perform(post("/api/script/execute").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"return $params.days + 1\",\"params\":{\"days\":1}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.value").value(2))
                .andExpect(jsonPath("$.engineVersion").value("v1"));

        mvc.perform(post("/api/script/execute").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"return 1\",\"engineVersion\":\"v0\",\"params\":{\"days\":90}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engineVersion").value("v0"))
                .andExpect(jsonPath("$.deprecationWarning").isNotEmpty());
    }

    @Test
    void executeRejectsRetiredAndUnknownVersion() throws Exception {
        mvc.perform(post("/api/script/execute").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"return 1\",\"engineVersion\":\"vEOL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("已下线")));

        mvc.perform(post("/api/script/execute").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"return 1\",\"engineVersion\":\"v999\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("未知引擎版本")));
    }

    @Test
    void parseCatchesSyntaxError() throws Exception {
        mvc.perform(post("/api/script/parse").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"return {\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("SYNTAX"));

        mvc.perform(post("/api/script/parse").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"return 1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}

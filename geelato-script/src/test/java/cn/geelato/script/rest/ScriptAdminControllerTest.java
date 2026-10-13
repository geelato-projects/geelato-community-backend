package cn.geelato.script.rest;

import cn.geelato.script.core.DefaultScriptEngine;
import cn.geelato.script.core.ScriptEngineBuilder;
import cn.geelato.script.core.ScriptExecution;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ScriptAdminControllerTest {

    private static final DefaultScriptEngine engine = new ScriptEngineBuilder()
            .defaultVersion("v1")
            .deriveRevision("v2", null, d -> {
            })
            .build();
    private static final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ScriptAdminController(engine, engine)).build();

    @AfterAll
    static void shutdown() {
        engine.close();
    }

    @Test
    void versionsListsAllRevisions() throws Exception {
        mvc.perform(get("/api/script/admin/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='v1')].id").value("v1"))
                .andExpect(jsonPath("$[?(@.id=='v2')].id").value("v2"));
    }

    @Test
    void setDefaultSwitchesHotAndAffectsExecution() throws Exception {
        mvc.perform(post("/api/script/admin/version/default").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionId\":\"v2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("v2"));
        try {
            assertEquals("v2", engine.execute(ScriptExecution.builder().content("return 1").build())
                    .getEngineVersion());
        } finally {
            mvc.perform(post("/api/script/admin/version/default").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"versionId\":\"v1\"}"));
        }
    }

    @Test
    void deprecateSetsHintAndStatus() throws Exception {
        mvc.perform(post("/api/script/admin/version/deprecate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionId\":\"v2\",\"migrateHint\":\"请迁移到 v1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("v2"))
                .andExpect(jsonPath("$.status").value("DEPRECATED"))
                .andExpect(jsonPath("$.migrateHint").value("请迁移到 v1"));
    }

    @Test
    void unknownVersionRejected() throws Exception {
        mvc.perform(post("/api/script/admin/version/default").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionId\":\"v999\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("未知引擎版本")));
    }
}

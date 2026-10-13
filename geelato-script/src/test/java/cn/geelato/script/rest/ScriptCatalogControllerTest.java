package cn.geelato.script.rest;

import cn.geelato.script.core.DefaultScriptEngine;
import cn.geelato.script.core.ScriptEngineBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ScriptCatalogControllerTest {

    private static final DefaultScriptEngine engine = new ScriptEngineBuilder().build();
    private static final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ScriptCatalogController(engine)).build();

    @AfterAll
    static void shutdown() {
        engine.close();
    }

    @Test
    void catalogReturnsBuiltinKitsWithFunctions() throws Exception {
        mvc.perform(get("/api/script/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.serviceName=='sql')].description").isNotEmpty())
                .andExpect(jsonPath("$[?(@.serviceName=='sql')].functions[*].name", hasItem("query")))
                .andExpect(jsonPath("$[?(@.serviceName=='http')].functions[*].name", hasItem("post")))
                .andExpect(jsonPath("$[?(@.serviceName=='encrypt')].functions[*].example", hasItem("$gl.encrypt.md5('text')")));
    }

    @Test
    void catalogRejectsUnknownProfile() throws Exception {
        mvc.perform(get("/api/script/catalog").param("profile", "nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("未知 ScriptProfile: nope（可用: [default]）"));
    }

    @Test
    void versionsListDefaultRevision() throws Exception {
        mvc.perform(get("/api/script/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("CURRENT"))
                .andExpect(jsonPath("$[0].namespaces", hasItem("sql")));
    }
}

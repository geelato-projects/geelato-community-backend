package cn.geelato.script.rest;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ScriptPlaygroundControllerTest {

    private static final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ScriptPlaygroundController()).build();

    @Test
    void servesPlaygroundHtml() throws Exception {
        mvc.perform(get("/script-playground.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("<title>Script Playground</title>")))
                .andExpect(content().string(containsString("/api/script/execute")));
    }

    @Test
    void servesWelcomePath() throws Exception {
        mvc.perform(get("/script-playground"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("<title>Script Playground</title>")));
    }
}

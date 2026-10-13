package cn.geelato.script.engine;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.geelato.script.core.DefaultScriptEngine;
import cn.geelato.script.core.ScriptEngineBuilder;
import cn.geelato.script.core.ScriptExecution;
import cn.geelato.script.core.ScriptProfile;
import cn.geelato.script.core.ScriptResult;
import cn.geelato.script.kit.KitContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 执行链路日志契约：START/BODY/PARAM/KIT/DONE/FAIL 以 execId 串联、超长截断。 */
class ScriptExecutionLogTest {

    private static final DefaultScriptEngine engine = new ScriptEngineBuilder()
            .statementLimit(2_000_000_000L)
            .profile(ScriptProfile.builder().id("judge")
                    .namespaces(Set.of("encrypt"))
                    .variables(Set.of(ScriptProfile.ALL))
                    .defaultTimeoutMs(5_000)
                    .build())
            .build();

    private ListAppender<ILoggingEvent> appender;

    @AfterAll
    static void shutdown() {
        engine.close();
    }

    @BeforeEach
    void attachAppender() {
        appender = new ListAppender<>();
        appender.start();
        engineLogger().addAppender(appender);
        kitLogger().addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        engineLogger().detachAppender(appender);
        kitLogger().detachAppender(appender);
    }

    private static Logger engineLogger() {
        return (Logger) LoggerFactory.getLogger(DefaultScriptEngine.class);
    }

    private static Logger kitLogger() {
        return (Logger) LoggerFactory.getLogger(KitContext.class);
    }

    private List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList());
    }

    @Test
    void successLogsAllStagesWithSameExecId() {
        ScriptResult result = engine.execute(ScriptExecution.builder()
                .scriptName("EXC-LOG/rule-1")
                .content("return 'ok-' + $params.days")
                .profileId("judge")
                .params(Map.of("days", 7))
                .build());
        assertEquals("SUCCESS", result.getStatus());
        List<String> msgs = messages();
        String start = msgs.stream().filter(m -> m.startsWith("SCRIPT-START")).findFirst().orElse("");
        assertTrue(start.contains("name='EXC-LOG/rule-1'"), start);
        assertTrue(start.contains("lang=js"), start);
        assertTrue(start.contains("dryRun="), start);
        assertTrue(msgs.stream().anyMatch(m -> m.startsWith("SCRIPT-BODY") && m.contains("return 'ok-'")));
        assertTrue(msgs.stream().anyMatch(m -> m.startsWith("SCRIPT-PARAM") && m.contains("\"days\":7")));
        String done = msgs.stream().filter(m -> m.startsWith("SCRIPT-DONE")).findFirst().orElse("");
        assertTrue(done.contains("status=SUCCESS"), done);
        assertTrue(done.contains("\"ok-7\""), done);
        String execId = result.getExecId();
        assertTrue(msgs.stream().filter(m -> m.contains("SCRIPT-")).allMatch(m -> m.contains("exec=" + execId)));
    }

    @Test
    void kitCallLogged() {
        ScriptResult result = engine.execute(ScriptExecution.builder()
                .content("return $gl.encrypt.md5('abc')")
                .profileId("judge")
                .build());
        assertEquals("SUCCESS", result.getStatus());
        assertTrue(messages().stream().anyMatch(m ->
                m.startsWith("KIT exec=") && m.contains("encrypt.md5") && m.contains("len=3")));
    }

    @Test
    void failureLoggedWithConsoleTail() {
        ScriptResult result = engine.execute(ScriptExecution.builder()
                .content("console.log('before-boom'); throw new Error('boom')")
                .profileId("judge")
                .build());
        assertEquals("ERROR", result.getStatus());
        List<String> msgs = messages();
        assertTrue(msgs.stream().anyMatch(m -> m.startsWith("SCRIPT-FAIL")
                && m.contains("status=ERROR") && m.contains("boom")));
        assertTrue(msgs.stream().anyMatch(m -> m.startsWith("CONSOLE") && m.contains("before-boom")));
    }

    @Test
    void timeoutLogged() {
        ScriptResult result = engine.execute(ScriptExecution.builder()
                .content("while (true) { }")
                .profileId("judge")
                .timeoutMs(300L)
                .build());
        assertEquals("TIMEOUT", result.getStatus());
        assertTrue(messages().stream().anyMatch(m -> m.startsWith("SCRIPT-FAIL") && m.contains("status=TIMEOUT")));
    }

    @Test
    void scriptBodyTruncatedWhenTooLong() {
        ScriptResult result = engine.execute(ScriptExecution.builder()
                .content("var s = '" + "a".repeat(3000) + "'; return 1")
                .profileId("judge")
                .build());
        assertEquals("SUCCESS", result.getStatus());
        assertTrue(messages().stream().anyMatch(m -> m.startsWith("SCRIPT-BODY") && m.contains("已截断")));
    }
}

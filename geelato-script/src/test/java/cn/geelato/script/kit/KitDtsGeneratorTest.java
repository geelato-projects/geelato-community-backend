package cn.geelato.script.kit;

import cn.geelato.core.graal.GraalFunction;
import cn.geelato.core.graal.GraalService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KitDtsGeneratorTest {

    @GraalService(name = "tpl", built = "false", descrption = "告警模板渲染")
    public static class TplService {

        @GraalFunction(example = "$tpl.render('FIN-001')", description = "按编码渲染模板")
        public String render(String code) {
            return code;
        }
    }

    @GraalService(name = "$legacy", built = "false", descrption = "历史 $ 声明兼容")
    public static class LegacyKit {

        @GraalFunction(example = "$legacy.tag()", description = "标记")
        public String tag() {
            return "legacy";
        }
    }

    @Test
    void generatesBuiltAndTopLevelDeclarations() {
        KitRegistry registry = new KitRegistry();
        registry.register(cn.geelato.script.kit.builtin.SqlKit.class);
        registry.register(cn.geelato.script.kit.builtin.MqlKit.class);
        registry.register(cn.geelato.script.kit.builtin.HttpKit.class);
        registry.register(cn.geelato.script.kit.builtin.EncryptKit.class);
        registry.register(TplService.class);
        registry.register(LegacyKit.class);

        String dts = KitDtsGenerator.generate(registry);

        assertTrue(dts.contains("declare const $gl: {"), dts);
        assertTrue(dts.contains("sql: {"), dts);
        assertTrue(dts.contains("query(sql: string, params: Record<string, unknown>): unknown;"), dts);
        assertTrue(dts.contains("@example $gl.sql.query('select id, name from t where status = :status', {status: 'OPEN'})"), dts);
        assertTrue(dts.contains("md5(text: string): string;"), dts);
        assertTrue(dts.contains("request(req: Record<string, unknown>): unknown;"), dts);
        assertTrue(dts.contains("declare const $tpl: {"), dts);
        assertTrue(dts.contains("render(code: string): string;"), dts);
        assertTrue(dts.contains("declare const $legacy:") && !dts.contains("$$legacy"), "历史 $ 声明不双写: " + dts);
    }
}

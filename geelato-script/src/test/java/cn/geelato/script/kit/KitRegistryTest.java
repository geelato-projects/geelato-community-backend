package cn.geelato.script.kit;

import cn.geelato.core.graal.GraalFunction;
import cn.geelato.core.graal.GraalService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitRegistryTest {

    @GraalService(name = "dup", built = "true")
    static class DupA {
        @GraalFunction(example = "a()")
        public String a() {
            return "a";
        }
    }

    @GraalService(name = "dup", built = "true")
    static class DupB {
        @GraalFunction(example = "b()")
        public String b() {
            return "b";
        }
    }

    @GraalService(name = "badReturn", built = "true")
    static class BadReturn {
        @GraalFunction(example = "x()")
        public java.io.File x() {
            return null;
        }
    }

    @GraalService(name = "good", built = "true")
    static class Good {
        @GraalFunction(example = "$gl.good.hello('w')", description = "问好")
        public String hello(String name) {
            return "hi " + name;
        }
    }

    /** 构造器非 (KitContext) 单参形状（如依赖 JdbcTemplate），只能经工厂通道注册。 */
    @GraalService(name = "factoryKit", built = "true")
    static class FactoryKit {
        public FactoryKit(String seed, KitContext ctx) {
        }

        @GraalFunction(example = "x()")
        public String x() {
            return "x";
        }
    }

    @Test
    void duplicateNameFailsFast() {
        KitRegistry registry = new KitRegistry();
        registry.register(new DupA());
        assertThrows(IllegalStateException.class, () -> registry.register(new DupB()));
    }

    @Test
    void unsupportedReturnTypeFailsFast() {
        KitRegistry registry = new KitRegistry();
        registry.register(new BadReturn());
        IllegalStateException ex = assertThrows(IllegalStateException.class, registry::validate);
        assertTrue(ex.getMessage().contains("BadReturn#x"));
    }

    @Test
    void catalogCarriesMetadata() {
        KitRegistry registry = new KitRegistry();
        registry.register(new Good());
        registry.validate();
        var catalog = registry.catalog(ns -> true);
        assertEquals(1, catalog.size());
        assertEquals("good", catalog.get(0).getServiceName());
        assertEquals("hello", catalog.get(0).getFunctions().get(0).getName());
        assertEquals("$gl.good.hello('w')", catalog.get(0).getFunctions().get(0).getExample());
    }

    @Test
    void factoryScopedKitWithCustomCtorNotTreatedAsSingleton() {
        KitRegistry registry = new KitRegistry();
        registry.registerScoped("factoryKit", FactoryKit.class, ctx -> new FactoryKit("seed", ctx));
        registry.validate();
        // 工厂通道注册即 scoped：不得走无参构造的单例实例化路径
        assertTrue(!registry.resolveSingletons().containsKey("factoryKit"));
        assertEquals(FactoryKit.class, registry.resolveScopedClasses().get("factoryKit"));
    }

    @Test
    void scanPackagesPicksUpDemoKit() {
        KitRegistry registry = new KitRegistry();
        registry.scanPackages(java.util.List.of("cn.geelato.script.kit.demo"));
        registry.validate();
        var catalog = registry.catalog(ns -> true);
        assertTrue(catalog.stream().anyMatch(s -> "demo".equals(s.getServiceName())));
    }
}

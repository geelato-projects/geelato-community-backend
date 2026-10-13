package cn.geelato.script.kit.demo;

import cn.geelato.core.graal.GraalFunction;
import cn.geelato.core.graal.GraalService;
import cn.geelato.script.kit.KitContext;

import java.util.Map;

/**
 * 演示 kit：SDK 不内置任何 kit，本类是消费方编写与注册 kit 的参考模板。
 *
 * 编写规则：
 * - 类上 {@code @GraalService}：name 为脚本侧命名空间（built="true" 挂到 $gl 下），descrption 进元数据清单；
 * - 对外方法标 {@code @GraalFunction}（example/description 进 catalog），参数与返回类型须在 interop 白名单内
 *   （String/数值/Boolean/Map/List/Date/Object/Value），违规启动即 fail-fast；
 * - 需要执行级上下文（execId、身份快照、attributes 等）时声明唯一 public 构造器 (KitContext)，
 *   即被判定为 scoped kit，每次执行实例化、互不串扰；无状态 kit 用无参构造器即可（单例复用）。
 *
 * 注册方式（三选一）：
 * 1. 配置扫描：geelato.script.engine.scan-packages 追加本包（cn.geelato.script.kit.demo）；
 * 2. 编程注册：new ScriptEngineBuilder().registerKit(DemoService.class)（或传实例，Spring Bean 优先传实例）；
 * 3. Spring 环境：定义 {@code ScriptEngineCustomizer} Bean，在 customize 中 builder.registerKit(bean)。
 */
@GraalService(name = "demo", built = "true", descrption = "演示 kit（模板）：hello/add")
public class DemoService {

    private final KitContext ctx;

    public DemoService(KitContext ctx) {
        this.ctx = ctx;
    }

    @GraalFunction(example = "$gl.demo.hello('world')", description = "回显问好并携带本次执行 execId")
    public Map<String, Object> hello(String name) {
        return Map.of(
                "message", "hello " + (name == null || name.isBlank() ? "world" : name),
                "execId", String.valueOf(ctx.getExecId()));
    }

    @GraalFunction(example = "$gl.demo.add(1, 2)", description = "两数相加")
    public long add(long a, long b) {
        return a + b;
    }
}

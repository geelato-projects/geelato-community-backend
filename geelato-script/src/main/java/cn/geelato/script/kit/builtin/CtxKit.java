package cn.geelato.script.kit.builtin;

import cn.geelato.core.graal.GraalFunction;
import cn.geelato.core.graal.GraalService;
import cn.geelato.script.kit.KitContext;

import java.util.Map;

/**
 * 执行上下文 kit（$gl.ctx）：读取调用方注入的业务上下文（bindings["$ctx"]），
 * 与 bindings 注入的 $ctx 变量同源双入口——kit 形态不受 profile.variables 白名单约束。
 */
@GraalService(name = "ctx", built = "true", descrption = "执行上下文（调用方注入的业务上下文，$ctx 同源）")
public class CtxKit {

    private final KitContext ctx;

    public CtxKit(KitContext ctx) {
        this.ctx = ctx;
    }

    @GraalFunction(example = "$gl.ctx.get('bizId')", description = "取上下文单键值（缺失返回 null）")
    public Object get(String key) {
        return cn.geelato.script.lang.GuestValues.toGuest(source().get(key));
    }

    @GraalFunction(example = "$gl.ctx.all()", description = "取完整上下文 Map（只读快照）")
    public Object all() {
        return cn.geelato.script.lang.GuestValues.toGuest(Map.copyOf(source()));
    }

    private Map<String, Object> source() {
        Object value = ctx.getBindings().get("$ctx");
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }
}

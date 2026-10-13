package cn.geelato.script.kit.builtin;

import cn.geelato.core.graal.GraalFunction;
import cn.geelato.core.graal.GraalService;
import cn.geelato.script.kit.KitContext;

import java.util.Map;

/**
 * 参数输入 kit（$gl.parameter）：读取调用方传入的脚本参数（ScriptExecution.params 归一的 $params），
 * 与 bindings 注入的 $params 变量同源双入口——kit 形态不受 profile.variables 白名单约束。
 */
@GraalService(name = "parameter", built = "true", descrption = "参数输入（调用方传入的脚本参数，$params 同源）")
public class ParameterKit {

    private final KitContext ctx;

    public ParameterKit(KitContext ctx) {
        this.ctx = ctx;
    }

    @GraalFunction(example = "$gl.parameter.get('days')", description = "取参数单键值（缺失返回 null）")
    public Object get(String key) {
        return cn.geelato.script.lang.GuestValues.toGuest(source().get(key));
    }

    @GraalFunction(example = "$gl.parameter.all()", description = "取完整参数 Map（只读快照）")
    public Object all() {
        return cn.geelato.script.lang.GuestValues.toGuest(Map.copyOf(source()));
    }

    private Map<String, Object> source() {
        Object value = ctx.getBindings().get("$params");
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }
}

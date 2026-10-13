package cn.geelato.script.core;

import lombok.Builder;
import lombok.Getter;

import java.util.Set;

/**
 * 场景能力档案：决定一次执行可见的 kit 命名空间与可注入变量，与引擎版本正交
 * （版本定"哪个版本的 kit"，profile 定"哪些命名空间可见"）。
 */
@Getter
@Builder
public class ScriptProfile {

    public static final String ALL = "*";
    /** 恒可见变量：不受 profile variables 约束。 */
    private static final Set<String> ALWAYS_VISIBLE_VARS = Set.of("console", "$env");

    private final String id;
    /** 可见 kit 命名空间（@GraalService name 集合）；含 "*" 表示全部。 */
    private final Set<String> namespaces;
    /** 允许注入的变量名（$ctx/$params/$input 等）；含 "*" 表示全部；console/$env 恒可用。 */
    private final Set<String> variables;
    /** Map 型绑定转不可变（$tpl 语义：不可在脚本中覆盖）。 */
    private final boolean frozenBindings;
    /** 本档案语句数上限（null 用全局）。 */
    private final Long statementLimit;
    /** 本档案缺省超时毫秒。 */
    private final long defaultTimeoutMs;

    public boolean namespaceAllowed(String namespace) {
        return namespaces == null || namespaces.contains(ALL) || namespaces.contains(namespace);
    }

    public boolean variableAllowed(String name) {
        return ALWAYS_VISIBLE_VARS.contains(name) || variables == null || variables.contains(ALL) || variables.contains(name);
    }
}

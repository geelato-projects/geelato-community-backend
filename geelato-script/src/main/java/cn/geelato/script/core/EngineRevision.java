package cn.geelato.script.core;

import cn.geelato.script.kit.KitDefinition;
import cn.geelato.script.kit.KitRegistry;
import cn.geelato.script.kit.ScopedKitSlot;
import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 引擎版本：脚本可观察契约的快照（kit 集合与方法签名、语言级别、变量约定）。
 * 破坏性变更时注册新 revision（换版本化 kit 类），旧 revision 继续服务老脚本并存。
 * 引擎核心（调度/超时/沙箱收口）对脚本不可见，升级不带版本。
 * status/migrateHint 为运行时可变状态（EngineAdmin 唯一写口，volatile 不重启生效），快照部分不可变。
 */
@Getter
public class EngineRevision {

    public enum Status { CURRENT, DEPRECATED, RETIRED }

    private final String id;
    private volatile Status status;
    private volatile String migrateHint;
    /** js.ecmascript-version 选项（null 用引擎缺省）。 */
    private final String esVersion;
    /** 该版本冻结的无状态 kit 快照：namespace -> 实例。 */
    private final Map<String, Object> singletonKits;
    /** 该版本每执行实例化的 scoped kit：namespace -> 槽位（类 + 可选工厂，不回退全局注册表）。 */
    private final Map<String, ScopedKitSlot> scopedKits;
    /** 该版本的 kit 定义快照：namespace -> 定义（catalog 输出与挂载元数据）。 */
    private final Map<String, KitDefinition> definitions;

    public EngineRevision(String id, Status status, String migrateHint, String esVersion,
                          Map<String, Object> singletonKits, Map<String, ScopedKitSlot> scopedKits,
                          Map<String, KitDefinition> definitions) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("引擎版本 id 为空");
        }
        this.id = id;
        this.status = status == null ? Status.CURRENT : status;
        this.migrateHint = migrateHint;
        this.esVersion = esVersion;
        this.singletonKits = Collections.unmodifiableMap(new LinkedHashMap<>(singletonKits));
        this.scopedKits = Collections.unmodifiableMap(new LinkedHashMap<>(scopedKits));
        this.definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
    }

    /** 从注册表当前状态拍快照（构建期/运行时派生的基线）。 */
    public static EngineRevision snapshotOf(String id, Status status, String migrateHint, String esVersion,
                                            KitRegistry registry) {
        return new EngineRevision(id, status, migrateHint, esVersion,
                registry.resolveSingletons(), registry.resolveScopedSlots(), new LinkedHashMap<>(registry.getDefinitions()));
    }

    void markDeprecated(String hint) {
        this.status = Status.DEPRECATED;
        this.migrateHint = hint;
    }

    void markRetired() {
        this.status = Status.RETIRED;
    }
}

package cn.geelato.script.core;

import java.util.function.Consumer;

/**
 * 引擎版本运行时管理面：状态与缺省版本为引擎内可变状态（volatile，无需重启）。
 * 与执行门面 ScriptEngine 分离，宿主按需注入。
 */
public interface EngineAdmin {

    /** 热切缺省版本（未显式指定 engineVersion 的执行即走它；RETIRED 版本不可设为缺省）。 */
    EngineVersionInfo setDefault(String versionId);

    /** 标记弃用（执行继续，回填 deprecationWarning 与 console 提示；至少须保留一个 CURRENT）。 */
    EngineVersionInfo deprecate(String versionId, String migrateHint);

    /** 下线（须先经 deprecate；此后执行拒绝，IllegalArgumentException）。 */
    EngineVersionInfo retire(String versionId);

    /** 运行时挂载新版本（id 重复抛错；kit 经 interop 白名单校验）。 */
    EngineVersionInfo registerRevision(EngineRevision revision);

    /** 以 baselineId（空=缺省版本）快照为底派生新版本（不注册，配 registerRevision 使用）。 */
    EngineRevision deriveRevision(String id, String baselineId, Consumer<RevisionDeriver> mutator);
}

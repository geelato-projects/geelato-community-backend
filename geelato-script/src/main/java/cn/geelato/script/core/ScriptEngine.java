package cn.geelato.script.core;

import cn.geelato.core.graal.GraalServiceDescription;

import java.util.List;

/**
 * 通用脚本执行引擎门面：单脚本执行 / 语法预检 / kit 清单 / 版本清单。
 * 多步管道等领域编排由消费方基于 execute 自行实现。
 */
public interface ScriptEngine {

    ScriptResult execute(ScriptExecution execution);

    /** 语法预检（不执行）；返回空列表表示通过。 */
    List<ScriptIssue> parseOnly(String lang, String content, String engineVersion);

    /** 输出 profile + 版本维度可见的 kit/方法/元数据清单（速查面板数据源）。 */
    List<GraalServiceDescription> kitCatalog(String profileId, String engineVersion);

    List<EngineVersionInfo> versions();
}

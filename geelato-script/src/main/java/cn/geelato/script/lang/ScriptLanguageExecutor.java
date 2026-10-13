package cn.geelato.script.lang;

import cn.geelato.script.core.ScriptIssue;
import org.graalvm.polyglot.Context;

import java.util.List;
import java.util.Map;

/**
 * 语言执行器 SPI：新语言（python/wasm/...）加实现即接入。
 */
public interface ScriptLanguageExecutor {

    String language();

    /**
     * 在已构建的 Context 中执行脚本并返回结果（unwrap 后的 Java 类型）。
     *
     * @param context  已挂 kit、已注入变量的沙箱 Context
     * @param content  脚本原文（js：IIFE 包装取 return；python：__result__ 约定）
     * @param stepName 步骤名（Source 名，错误定位用）
     */
    Object execute(Context context, String content, String stepName);

    /** 语法预检（不执行），问题清单空为通过。 */
    List<ScriptIssue> parseOnly(Context context, String content, String stepName);

    /** 语言侧的变量注入约定（js：JSON 字面量 eval 为原生对象，frozen 时 Object.freeze；python：全局注入）。 */
    void bindVariables(Context context, Map<String, Object> variables, boolean frozen);
}

package cn.geelato.ide.service;

import cn.geelato.core.graal.GraalManager;
import cn.geelato.core.graal.GraalServiceDescription;
import cn.geelato.core.graal.GraalFunctionDescription;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityLiteMeta;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 上下文包服务。
 * <p>
 * 给 IDE 插件 / MCP / AI 提供"geelato 全貌"：
 * <ul>
 *   <li>实体目录（动态：MetaManager）</li>
 *   <li>MQL 文法（静态：resources 下）</li>
 *   <li>桥服务签名表（静态：graal-signatures.yaml + 动态：GraalManager 描述）</li>
 *   <li>语言使用指南（静态：JS/Python/Wasm）</li>
 *   <li>API 端点目录（静态：SrvExplain manifest）</li>
 * </ul>
 *
 * @author geelato
 */
@Service
@Slf4j
public class IdeContextService {

    private static final String SIGNATURES_PATH = "geelato/web/platform/ide/graal-signatures.yaml";

    @Value("${geelato.ide.context.mql-grammar-path:geelato/web/platform/ide/mql-grammar.md}")
    private String mqlGrammarPath;

    /**
     * 实体目录（来自 MetaManager，动态）。
     */
    public Map<String, Object> entities() {
        Map<String, Object> result = new LinkedHashMap<>();
        MetaManager mm = MetaManager.singleInstance();
        Collection<EntityLiteMeta> all;
        try {
            all = mm.getAllEntityLiteMetas();
        } catch (Exception e) {
            log.warn("获取实体目录失败", e);
            result.put("error", e.getMessage());
            return result;
        }
        // 按 catalog 分组（platform / 业务）
        Map<String, List<EntityLiteMeta>> byCatalog = new LinkedHashMap<>();
        if (all != null) {
            for (EntityLiteMeta e : all) {
                String catalog = e.getEntityType() != null ? e.getEntityType() : "other";
                byCatalog.computeIfAbsent(catalog, k -> new ArrayList<>()).add(e);
            }
        }
        result.put("total", all != null ? all.size() : 0);
        result.put("byCatalog", byCatalog);
        return result;
    }

    /**
     * 桥服务清单（动态：GraalManager 描述 + 静态：签名表）。
     */
    public Map<String, Object> graalServices() {
        Map<String, Object> result = new LinkedHashMap<>();
        // 动态部分：GraalManager 描述
        List<Map<String, Object>> dynamic = new ArrayList<>();
        for (GraalServiceDescription svc : GraalManager.singleInstance().getGraalServiceDescriptions()) {
            Map<String, Object> svcMap = new LinkedHashMap<>();
            svcMap.put("name", svc.getServiceName());
            svcMap.put("description", svc.getDescription());
            List<Map<String, String>> fns = new ArrayList<>();
            if (svc.getFunctions() != null) {
                for (GraalFunctionDescription fn : svc.getFunctions()) {
                    Map<String, String> fnMap = new LinkedHashMap<>();
                    fnMap.put("name", fn.getName());
                    fnMap.put("example", fn.getExample());
                    fnMap.put("description", fn.getDescription());
                    fns.add(fnMap);
                }
            }
            svcMap.put("functions", fns);
            dynamic.add(svcMap);
        }
        result.put("runtimeDescriptions", dynamic);
        // 静态部分：签名表
        result.put("signatures", loadSignatures());
        return result;
    }

    /**
     * 一键打包全部上下文。
     */
    public Map<String, Object> all() {
        Map<String, Object> all = new LinkedHashMap<>();
        all.put("entities", entities());
        all.put("mqlGrammar", mqlGrammar());
        all.put("graalServices", graalServices());
        all.put("languages", languagesGuide());
        all.put("apiEndpoints", apiEndpoints());
        return all;
    }

    /**
     * MQL 文法摘要（静态 markdown，来自 classpath）。
     */
    public String mqlGrammar() {
        return loadClasspathResource(mqlGrammarPath,
                "# MQL Grammar\n（占位文档，由 M4 完善；详见 document/MQL使用说明.md）");
    }

    /**
     * 三语言使用指南。
     */
    public Map<String, Object> languagesGuide() {
        Map<String, Object> guide = new LinkedHashMap<>();
        guide.put("js", jsGuide());
        guide.put("python", pythonGuide());
        guide.put("wasm", wasmGuide());
        return guide;
    }

    /**
     * API 端点目录（来自 SrvExplain manifest，静态文件）。
     */
    public Map<String, Object> apiEndpoints() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String manifest = loadClasspathResource("SrvExplain/_meta/manifest.json", null);
            if (manifest != null) {
                result.put("source", "SrvExplain");
                result.put("manifest", com.alibaba.fastjson2.JSON.parse(manifest));
            } else {
                result.put("source", "unavailable");
                result.put("note", "SrvExplain manifest 不在 classpath，请运行 geelato-srvexplain-generator");
            }
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }

    // ======================================================================
    //                            helpers
    // ======================================================================

    private Map<String, Object> loadSignatures() {
        try (InputStream is = openClasspath(SIGNATURES_PATH)) {
            if (is == null) {
                Map<String, Object> missing = new HashMap<>();
                missing.put("available", false);
                missing.put("note", "graal-signatures.yaml 未找到");
                return missing;
            }
            Yaml yaml = new Yaml();
            Map<String, Object> loaded = yaml.load(is);
            return loaded != null ? loaded : new HashMap<>();
        } catch (Exception e) {
            log.warn("加载签名表失败: {}", SIGNATURES_PATH, e);
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return err;
        }
    }

    private String loadClasspathResource(String path, String defaultValue) {
        if (path == null || path.isBlank()) {
            return defaultValue;
        }
        try (InputStream is = openClasspath(path)) {
            if (is == null) {
                return defaultValue;
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.debug("classpath 资源读取失败: {}", path, e);
            return defaultValue;
        }
    }

    private InputStream openClasspath(String path) throws IOException {
        // 兼容带 / 和不带 / 两种写法
        Resource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            return null;
        }
        return resource.getInputStream();
    }

    private Map<String, Object> jsGuide() {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("language", "js");
        g.put("description", "GraalJS（默认，最轻量，业务编排主力）");
        g.put("template", "// 脚本入口是 IIFE 内部，直接 return 结果\n" +
                "// $gl.* 桥服务可调用\n" +
                "var rows = $gl.dao.queryForMapList(\"order_info|user_id=${parameter.userId}\");\n" +
                "return rows;");
        g.put("bridgeAccess", "$gl.dao, $gl.http, $gl.fn, $gl.user 等（$gl.<service>.<method>）");
        g.put("notes", new String[]{
                "脚本必须是单一函数体（被自动包成 IIFE）",
                "$gl.vars 用于跨调用共享变量",
                "$gl.user / $gl.tenant 反映当前调用者"
        });
        return g;
    }

    private Map<String, Object> pythonGuide() {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("language", "python");
        g.put("description", "GraalPython（数据/AI 生态，pandas/numpy 可用）");
        g.put("template", "# 通过 polyglot 拿到 $gl 桥\n" +
                "gl = polyglot.import_value(\"$gl\")\n" +
                "rows = gl.dao.queryForMapList(\"order_info|user_id=1\")\n" +
                "__result__ = rows");
        g.put("bridgeAccess", "gl = polyglot.import_value(\"$gl\"); gl.dao.queryForMapList(...)");
        g.put("notes", new String[]{
                "结果赋值给 __result__ 全局变量",
                "默认禁用文件 IO（geelato.worker.graal.python-allow-io 控制）",
                "适合数据清洗、报表、AI 推理"
        });
        return g;
    }

    private Map<String, Object> wasmGuide() {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("language", "wasm");
        g.put("description", "GraalWasm（强沙箱，适合不可信代码和高性能计算）");
        g.put("template", "# content 为 base64 编码的 wasm 模块字节\n" +
                "# 模块需符合 WASI 规范\n" +
                "# 实例化后调用导出函数（默认 _start）");
        g.put("bridgeAccess", "（暂不支持桥服务直接调用，需通过 WASI import）");
        g.put("notes", new String[]{
                "需要符合 WASI 规范才能加载",
                "天然沙箱，不能直接访问宿主",
                "适合不可信 AI 产物、跨团队共享库"
        });
        return g;
    }
}

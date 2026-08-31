package cn.geelato.metasync.api;

import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.metasync.core.ConsistencyChecker;
import cn.geelato.metasync.core.EntitySyncReport;
import cn.geelato.metasync.core.MetaSourceLoader;
import cn.geelato.metasync.fix.JavaSourceWriter;
import cn.geelato.metasync.fix.JavaToMetaFixer;
import cn.geelato.metasync.fix.TableToMetaFixer;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.common.annotation.ApiRuntimeRestController;
import cn.geelato.web.common.constants.MediaTypes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 实体三者同步工具数据接口（自动加 /api 前缀 → /api/meta-sync/**）。
 *
 * @author geemeta
 */
@ApiRuntimeRestController("/meta-sync")
@Slf4j
public class MetaSyncApiController {

    @Autowired
    private MetaSourceLoader metaSourceLoader;
    @Autowired
    private ConsistencyChecker consistencyChecker;
    @Autowired
    private TableToMetaFixer tableToMetaFixer;
    @Autowired
    private JavaSourceWriter javaSourceWriter;
    @Autowired
    private JavaToMetaFixer javaToMetaFixer;

    @Value("${geelato.meta.scan-package-names:cn.geelato}")
    private String scanPackage;

    /** GET /api/meta-sync/scan?baseline=table|java|meta — 全量扫描三方，按基准对比（直接 IO，每次查库） */
    @RequestMapping(value = {"/scan"}, method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<?> scan(@org.springframework.web.bind.annotation.RequestParam(value = "baseline", required = false, defaultValue = "table") String baseline) {
        try {
            metaSourceLoader.setScanPackage(scanPackage);
            List<EntitySyncReport> reports = consistencyChecker.checkAll(baseline);
            Map<String, Object> summary = new HashMap<>();
            summary.put("total", reports.size());
            summary.put("consistent", reports.stream().filter(EntitySyncReport::isConsistent).count());
            summary.put("inconsistent", reports.stream().filter(r -> !r.isConsistent()).count());
            Map<String, Object> result = new HashMap<>();
            result.put("baseline", cn.geelato.metasync.core.ConsistencyChecker.normalizeBaseline(baseline));
            result.put("summary", summary);
            result.put("reports", reports);
            return ApiResult.success(result);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    /**
     * GET /api/meta-sync/check/{tableName}?baseline=... — 单实体校验（按基准）。
     * <p>直接 IO 重新查询该实体的三个源，不全量扫描；补偿后立即调本接口即可看到最新结果。
     */
    @GetMapping(value = {"/check/{tableName}"}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<?> checkSingle(@PathVariable("tableName") String tableName,
                                    @org.springframework.web.bind.annotation.RequestParam(value = "baseline", required = false, defaultValue = "table") String baseline) {
        try {
            metaSourceLoader.setScanPackage(scanPackage);
            return ApiResult.success(consistencyChecker.checkSingle(tableName, baseline));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    /** GET /api/meta-sync/diff/{tableName}?baseline=... — 单个实体的差异明细（按基准，直接 IO 单实体装载） */
    @GetMapping(value = {"/diff/{tableName}"}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<?> diff(@PathVariable("tableName") String tableName,
                             @org.springframework.web.bind.annotation.RequestParam(value = "baseline", required = false, defaultValue = "table") String baseline) {
        try {
            metaSourceLoader.setScanPackage(scanPackage);
            return ApiResult.success(consistencyChecker.checkSingle(tableName, baseline));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    /** GET /api/meta-sync/entities — 列出所有实体名（供下拉） */
    @GetMapping(value = {"/entities"}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<?> entities() {
        try {
            metaSourceLoader.setScanPackage(scanPackage);
            metaSourceLoader.load();
            List<Map<String, String>> list = new ArrayList<>();
            for (String tableName : metaSourceLoader.getAllTableNames()) {
                EntityMeta metaEm = metaSourceLoader.getMetaEntity(tableName);
                EntityMeta javaEm = metaSourceLoader.getJavaEntity(tableName);
                Map<String, String> item = new HashMap<>();
                item.put("tableName", tableName);
                String entityName = metaEm != null && StringUtils.isNotBlank(metaEm.getEntityName())
                        ? metaEm.getEntityName()
                        : (javaEm != null && StringUtils.isNotBlank(javaEm.getEntityName()) ? javaEm.getEntityName() : tableName);
                item.put("entityName", entityName);
                String title = metaEm != null && StringUtils.isNotBlank(metaEm.getEntityTitle())
                        ? metaEm.getEntityTitle()
                        : (javaEm != null && StringUtils.isNotBlank(javaEm.getEntityTitle()) ? javaEm.getEntityTitle() : tableName);
                item.put("title", title);
                item.put("tableType", metaSourceLoader.isView(tableName) ? "view" : "entity");
                list.add(item);
            }
            return ApiResult.success(list);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    /** POST /api/meta-sync/fix/table-to-meta — 物理表→实体定义（视图不可补偿） */
    @PostMapping(value = {"/fix/table-to-meta"}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<?> fixTableToMeta(@RequestBody Map<String, Object> body) {
        try {
            String tableName = str(body, "tableName");
            if (metaSourceLoader.isView(tableName)) {
                return ApiResult.fail("「" + tableName + "」是视图，视图不支持补偿");
            }
            String entityName = str(body, "entityName");
            boolean apply = bool(body, "apply", false);
            TableToMetaFixer.FixResult r = tableToMetaFixer.syncTableToMeta(tableName, entityName, null, apply);
            return ApiResult.success(r);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    /** POST /api/meta-sync/fix/gen-java — 实体定义/物理表→Java源码 */
    @PostMapping(value = {"/fix/gen-java"}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<?> fixGenJava(@RequestBody Map<String, Object> body) {
        try {
            String entityName = str(body, "entityName");
            String packageName = str(body, "packageName");
            if (StringUtils.isBlank(packageName)) {
                packageName = "cn.geelato.meta";
            }
            String source = javaSourceWriter.generate(entityName, packageName);
            Map<String, String> result = new HashMap<>();
            result.put("source", source);
            result.put("entityName", entityName);
            return ApiResult.success(result);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    /** POST /api/meta-sync/fix/java-to-meta — Java类→实体定义（视图不可补偿） */
    @PostMapping(value = {"/fix/java-to-meta"}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<?> fixJavaToMeta(@RequestBody Map<String, Object> body) {
        try {
            String tableName = str(body, "tableName");
            if (metaSourceLoader.isView(tableName)) {
                return ApiResult.fail("「" + tableName + "」是视图，视图不支持补偿");
            }
            String entityName = str(body, "entityName");
            String output = str(body, "output");
            if (StringUtils.isBlank(output)) {
                output = "sql"; // 默认只返回 SQL，不写库
            }
            JavaToMetaFixer.FixResult r = javaToMetaFixer.genMeta(entityName, output);
            return ApiResult.success(r);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }

    private static boolean bool(Map<String, Object> body, String key, boolean def) {
        Object v = body.get(key);
        if (v == null) {
            return def;
        }
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        return Boolean.parseBoolean(v.toString());
    }
}

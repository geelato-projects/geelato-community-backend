package cn.geelato.ide.entitydef.controller;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.DesignTimeApiRestController;
import cn.geelato.ide.entitydef.dto.IdeEntityDefinition;
import cn.geelato.ide.entitydef.dto.IdeEntityValidateResult;
import cn.geelato.ide.entitydef.service.IdeEntityDdlService;
import cn.geelato.ide.entitydef.service.IdeEntityService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IDE 实体定义 Controller（设计态）。
 * <p>
 * 路径前缀 {@code /ide/entity}。所有写操作复用现有 {@code DefaultSecurityInterceptor} 鉴权。
 * 路径设计避免与脚本 {@code /ide/script} 冲突。
 *
 * @author geelato
 */
@DesignTimeApiRestController("/ide/entity")
@Slf4j
public class IdeEntityController {

    @Autowired
    private IdeEntityService ideEntityService;

    @Autowired
    private IdeEntityDdlService ideEntityDdlService;

    @Value("${geelato.ide.entity.prod-requires-confirm:true}")
    private boolean prodRequiresConfirm;

    /**
     * 列出实体。
     */
    @GetMapping("/list")
    public ApiResult<?> list(@RequestParam(required = false) String connectId,
                             @RequestParam(required = false) String keyword) {
        return ApiResult.success(ideEntityService.list(connectId, keyword));
    }

    /**
     * 按 entityName 取完整定义。
     */
    @GetMapping("/get/{entityName}")
    public ApiResult<?> get(@PathVariable String entityName) {
        IdeEntityDefinition def = ideEntityService.getByEntityName(entityName);
        if (def == null) {
            return ApiResult.fail("实体不存在: " + entityName);
        }
        return ApiResult.success(def);
    }

    /**
     * 列出已用 connectId（供插件的 connectId 选择器）。
     */
    @GetMapping("/connectIds")
    public ApiResult<?> connectIds() {
        return ApiResult.success(ideEntityService.listConnectIds());
    }

    /**
     * dry-run 校验（不落库）。AI 自检闭环的核心入口。
     */
    @PostMapping("/validate")
    public ApiResult<?> validate(@RequestBody IdeEntityDefinition def) {
        IdeEntityValidateResult result = ideEntityService.validate(def);
        return ApiResult.success(result);
    }

    /**
     * 创建/更新实体定义（写元数据，不建物理表）。完成后 MetaManager 立即识别。
     */
    @PostMapping("/define")
    public ApiResult<?> define(@RequestBody IdeEntityDefinition def, HttpServletRequest request) {
        try {
            IdeEntityDefinition created = ideEntityService.define(def);
            Map<String, Object> extra = new HashMap<>();
            extra.put("definition", created);
            extra.put("crudUrls", buildCrudUrls(created.getEntityName()));
            return ApiResult.success(extra);
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("定义实体失败: {}", def.getEntityName(), e);
            return ApiResult.fail("定义失败: " + e.getMessage());
        }
    }

    /**
     * 预览 DDL（不执行）。返回 CREATE TABLE 或 ALTER TABLE SQL 字符串。
     */
    @PostMapping("/previewDdl")
    public ApiResult<?> previewDdl(@RequestBody IdeEntityDefinition def) {
        try {
            IdeEntityValidateResult vr = ideEntityService.validate(def);
            if (!vr.isValid()) {
                return ApiResult.fail("校验失败: " + String.join("; ", vr.getErrors()));
            }
            String ddl = ideEntityDdlService.previewDdl(def);
            Map<String, Object> result = new HashMap<>();
            result.put("sql", ddl);
            result.put("operation", ddl.startsWith("CREATE") ? "CREATE" : "ALTER");
            return ApiResult.success(result);
        } catch (Exception e) {
            log.error("预览 DDL 失败: {}", def.getEntityName(), e);
            return ApiResult.fail("预览失败: " + e.getMessage());
        }
    }

    /**
     * 物理建表/改表（按 connectId 切库执行）。prod 环境需 confirm=true。
     */
    @PostMapping("/createTable/{entityName}")
    public ApiResult<?> createTable(@PathVariable String entityName,
                                    @RequestParam(required = false, defaultValue = "false") boolean confirm,
                                    HttpServletRequest request) {
        IdeEntityDefinition def = ideEntityService.getByEntityName(entityName);
        if (def == null) {
            return ApiResult.fail("实体不存在: " + entityName);
        }
        // prod 二次确认（MVP 阶段：envScope 不在 DTO 里，先按 connectId 命名约定判断；可由插件端控制）
        // 这里只做硬防护：所有 createTable 默认要求显式 confirm=true（避免误触发）
        if (prodRequiresConfirm && !confirm) {
            return ApiResult.fail(409, "物理建表需要显式确认（请传 confirm=true）");
        }
        try {
            String sql = ideEntityDdlService.executeDdl(def);
            Map<String, Object> result = new HashMap<>();
            result.put("sql", sql);
            result.put("executed", true);
            return ApiResult.success(result);
        } catch (Exception e) {
            log.error("物理建表失败: {}", entityName, e);
            return ApiResult.fail("建表失败: " + e.getMessage());
        }
    }

    /**
     * 逻辑删除。
     */
    @DeleteMapping("/delete/{entityName}")
    public ApiResult<?> delete(@PathVariable String entityName) {
        try {
            ideEntityService.delete(entityName);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            return ApiResult.fail("删除失败: " + e.getMessage());
        }
    }

    // ======================================================================
    //                            helpers
    // ======================================================================

    private Map<String, String> buildCrudUrls(String entityName) {
        Map<String, String> urls = new HashMap<>();
        urls.put("list", "/api/meta/list?entity=" + entityName);
        urls.put("save", "/api/meta/save/" + entityName);
        urls.put("delete", "/api/meta/delete/" + entityName + "/{id}");
        return urls;
    }
}

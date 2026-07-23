package cn.geelato.ide.controller;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.User;
import cn.geelato.web.common.annotation.DesignTimeApiRestController;
import cn.geelato.ide.dto.IdeScriptCreateRequest;
import cn.geelato.ide.dto.IdeScriptUpdateRequest;
import cn.geelato.ide.entity.IdeScript;
import cn.geelato.ide.entity.IdeSyncLog;
import cn.geelato.ide.enums.IdeSyncAction;
import cn.geelato.ide.enums.IdeSyncDirection;
import cn.geelato.ide.enums.IdeSyncResult;
import cn.geelato.ide.service.IdeOptimisticLockException;
import cn.geelato.ide.service.IdeScriptService;
import cn.geelato.ide.service.IdeSyncLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * IDE 脚本 CRUD Controller（设计态接口）。
 * <p>
 * 路径前缀 {@code /ide/script}，默认被 {@code DefaultSecurityInterceptor} 拦截鉴权。
 * 所有写操作都记审计到 {@code ide_sync_log}。
 *
 * @author geelato
 */
@DesignTimeApiRestController("/ide/script")
@Slf4j
public class IdeScriptController {

    @Autowired
    private IdeScriptService ideScriptService;

    @Autowired
    private IdeSyncLogService ideSyncLogService;

    /**
     * 列表查询（含分页）。
     */
    @PostMapping("/list")
    public ApiResult<?> list(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        Map<String, Object> params = new HashMap<>();
        int page = 1;
        int size = 20;
        if (body != null) {
            params.putAll(body);
            Object p = body.get("page");
            Object s = body.get("size");
            if (p instanceof Number) {
                page = ((Number) p).intValue();
            }
            if (s instanceof Number) {
                size = ((Number) s).intValue();
            }
            params.remove("page");
            params.remove("size");
        }
        params.put("tenant_code", resolveTenantCode());
        return ApiResult.success(ideScriptService.list(params, page, size));
    }

    /**
     * 按 code 取详情。
     */
    @GetMapping("/get/{code}")
    public ApiResult<?> get(@PathVariable("code") String code) {
        IdeScript script = ideScriptService.getByCode(code);
        if (script == null) {
            return ApiResult.fail("脚本不存在: " + code);
        }
        return ApiResult.success(script);
    }

    /**
     * 分组列表（脚本树用）。
     */
    @GetMapping("/groups")
    public ApiResult<?> groups() {
        return ApiResult.success(ideScriptService.groups());
    }

    /**
     * 创建脚本。
     */
    @PostMapping("/create")
    public ApiResult<?> create(@RequestBody IdeScriptCreateRequest req, HttpServletRequest request) {
        try {
            IdeScript script = toEntity(req);
            IdeScript created = ideScriptService.create(script);
            // 审计
            recordSyncLog(IdeSyncAction.CREATE, null, created, IdeSyncResult.SUCCESS, "创建", request);
            return ApiResult.success(created);
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("创建脚本失败", e);
            recordSyncLog(IdeSyncAction.CREATE, null, null, IdeSyncResult.FAIL, e.getMessage(), request);
            return ApiResult.fail("创建失败: " + e.getMessage());
        }
    }

    /**
     * 更新脚本（乐观锁）。
     */
    @PostMapping("/update/{code}")
    public ApiResult<?> update(@PathVariable("code") String code,
                               @RequestBody IdeScriptUpdateRequest req,
                               HttpServletRequest request) {
        try {
            if (req.getVersion() == null) {
                return ApiResult.fail("version 不能为空（乐观锁必需）");
            }
            IdeScript newFields = toEntity(req);
            IdeScript before = ideScriptService.getByCode(code);
            IdeScript updated = ideScriptService.update(code, newFields, req.getVersion());
            // 审计
            IdeSyncLog logEntry = buildLog(IdeSyncAction.UPDATE, IdeSyncDirection.FILE_TO_DB,
                    before, updated, IdeSyncResult.SUCCESS, "更新成功", request);
            ideSyncLogService.record(logEntry);
            return ApiResult.success(updated);
        } catch (IdeOptimisticLockException e) {
            log.warn("脚本更新乐观锁冲突: code={}, {}", code, e.getMessage());
            recordSyncLog(IdeSyncAction.UPDATE, null, null, IdeSyncResult.REJECTED, e.getMessage(), request);
            return ApiResult.fail(409, e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("更新脚本失败: code={}", code, e);
            recordSyncLog(IdeSyncAction.UPDATE, null, null, IdeSyncResult.FAIL, e.getMessage(), request);
            return ApiResult.fail("更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除脚本（逻辑删除）。
     */
    @DeleteMapping("/delete/{code}")
    public ApiResult<?> delete(@PathVariable("code") String code, HttpServletRequest request) {
        try {
            IdeScript before = ideScriptService.getByCode(code);
            if (before == null) {
                return ApiResult.fail("脚本不存在: " + code);
            }
            ideScriptService.delete(code);
            recordSyncLog(IdeSyncAction.DELETE, before, null, IdeSyncResult.SUCCESS, "删除成功", request);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error("删除脚本失败: code={}", code, e);
            recordSyncLog(IdeSyncAction.DELETE, null, null, IdeSyncResult.FAIL, e.getMessage(), request);
            return ApiResult.fail("删除失败: " + e.getMessage());
        }
    }

    /**
     * 发布脚本：DRAFT → PUBLISHED。
     */
    @PostMapping("/publish/{code}")
    public ApiResult<?> publish(@PathVariable("code") String code, HttpServletRequest request) {
        try {
            IdeScript published = ideScriptService.publish(code);
            recordSyncLog(IdeSyncAction.PUBLISH, null, published, IdeSyncResult.SUCCESS, "发布成功", request);
            return ApiResult.success(published);
        } catch (Exception e) {
            log.error("发布脚本失败: code={}", code, e);
            recordSyncLog(IdeSyncAction.PUBLISH, null, null, IdeSyncResult.FAIL, e.getMessage(), request);
            return ApiResult.fail("发布失败: " + e.getMessage());
        }
    }

    // ======================================================================
    //                            helpers
    // ======================================================================

    private IdeScript toEntity(IdeScriptCreateRequest req) {
        IdeScript s = new IdeScript();
        s.setCode(req.getCode());
        s.setName(req.getName());
        s.setGroupName(req.getGroupName());
        s.setLanguage(req.getLanguage());
        s.setContent(req.getContent());
        s.setWasmBinaryBase64(req.getWasmBinaryBase64());
        s.setEnvScope(req.getEnvScope());
        s.setDescription(req.getDescription());
        s.setDefaultParams(req.getDefaultParams());
        return s;
    }

    private IdeScript toEntity(IdeScriptUpdateRequest req) {
        IdeScript s = new IdeScript();
        s.setName(req.getName());
        s.setGroupName(req.getGroupName());
        s.setLanguage(req.getLanguage());
        s.setContent(req.getContent());
        s.setWasmBinaryBase64(req.getWasmBinaryBase64());
        s.setEnvScope(req.getEnvScope());
        s.setDescription(req.getDescription());
        s.setDefaultParams(req.getDefaultParams());
        return s;
    }

    private void recordSyncLog(String action, IdeScript before, IdeScript after, String result, String message, HttpServletRequest request) {
        IdeSyncLog logEntry = buildLog(action, null, before, after, result, message, request);
        ideSyncLogService.record(logEntry);
    }

    private IdeSyncLog buildLog(String action, String direction, IdeScript before, IdeScript after, String result, String message, HttpServletRequest request) {
        IdeSyncLog logEntry = new IdeSyncLog();
        logEntry.setAction(action);
        if (Strings.isNotBlank(direction)) {
            logEntry.setDirection(direction);
        }
        logEntry.setResult(result);
        logEntry.setMessage(message);
        logEntry.setCreateAt(new Date());
        if (before != null) {
            logEntry.setScriptId(before.getId());
            logEntry.setScriptCode(before.getCode());
            logEntry.setBeforeHash(before.getFileHash());
            logEntry.setBeforeVersion(before.getVersion());
        }
        if (after != null) {
            logEntry.setScriptId(after.getId());
            logEntry.setScriptCode(after.getCode());
            logEntry.setAfterHash(after.getFileHash());
            logEntry.setAfterVersion(after.getVersion());
        }
        if (request != null) {
            logEntry.setClientIp(resolveClientIp(request));
            logEntry.setUserAgent(request.getHeader("User-Agent"));
        }
        return logEntry;
    }

    private String resolveTenantCode() {
        // 优先 SecurityContext，其次 header
        try {
            if (SecurityContext.getCurrentTenant() != null) {
                return SecurityContext.getCurrentTenant().getCode();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (Strings.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (Strings.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}

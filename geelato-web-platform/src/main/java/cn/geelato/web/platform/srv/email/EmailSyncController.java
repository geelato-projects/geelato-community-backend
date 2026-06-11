package cn.geelato.web.platform.srv.email;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.security.SecurityContext;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.email.service.EmailSyncService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 邮件同步管理接口
 */
@ApiRestController("/email/sync")
@Slf4j
public class EmailSyncController extends BaseController {

    @Autowired
    private EmailSyncService emailSyncService;

    /**
     * 手动触发同步（异步执行）
     * Body: { "emailAccountId": "xxx" }
     */
    @PostMapping("/trigger")
    public ApiResult<String> triggerSync(@RequestBody Map<String, Object> body) {
        try {
            String userId = SecurityContext.getCurrentUser().getUserId();
            if (Strings.isBlank(userId)) {
                return ApiResult.fail("用户未登录");
            }
            String emailAccountId = body != null ? String.valueOf(body.get("emailAccountId")) : null;
            if (Strings.isBlank(emailAccountId)) {
                return ApiResult.fail("emailAccountId 不能为空");
            }
            log.info("手动触发邮件同步, userId={}, emailAccountId={}", userId, emailAccountId);
            // 异步执行，不阻塞响应
            CompletableFuture.runAsync(() -> {
                emailSyncService.syncAccount(userId, emailAccountId);
            });
            return ApiResult.success("同步任务已提交");
        } catch (Exception e) {
            log.error("手动触发同步失败", e);
            return ApiResult.fail("触发同步失败: " + e.getMessage());
        }
    }

    /**
     * 手动同步单个文件夹
     * Body: { "emailAccountId": "xxx", "folder": "INBOX" }
     */
    @PostMapping("/trigger/folder")
    public ApiResult<String> triggerFolderSync(@RequestBody Map<String, Object> body) {
        try {
            String userId = SecurityContext.getCurrentUser().getUserId();
            if (Strings.isBlank(userId)) {
                return ApiResult.fail("用户未登录");
            }
            String emailAccountId = body != null ? String.valueOf(body.get("emailAccountId")) : null;
            String folder = body != null ? String.valueOf(body.get("folder")) : null;
            if (Strings.isBlank(emailAccountId) || Strings.isBlank(folder)) {
                return ApiResult.fail("emailAccountId 和 folder 不能为空");
            }
            log.info("手动触发文件夹同步, userId={}, emailAccountId={}, folder={}", userId, emailAccountId, folder);
            emailSyncService.syncSingleFolder(userId, emailAccountId, folder);
            return ApiResult.success("文件夹同步完成");
        } catch (Exception e) {
            log.error("文件夹同步失败", e);
            return ApiResult.fail("文件夹同步失败: " + e.getMessage());
        }
    }

    /**
     * 开启/关闭同步
     * Body: { "emailAccountId": "xxx", "enabled": true/false }
     */
    @PostMapping("/toggle")
    public ApiResult<String> toggleSync(@RequestBody Map<String, Object> body) {
        try {
            String userId = SecurityContext.getCurrentUser().getUserId();
            if (Strings.isBlank(userId)) {
                return ApiResult.fail("用户未登录");
            }
            String emailAccountId = body != null ? String.valueOf(body.get("emailAccountId")) : null;
            Object enabledObj = body != null ? body.get("enabled") : null;
            if (Strings.isBlank(emailAccountId) || enabledObj == null) {
                return ApiResult.fail("emailAccountId 和 enabled 不能为空");
            }
            boolean enabled = parseBoolean(enabledObj);
            emailSyncService.toggleSync(userId, emailAccountId, enabled);
            return ApiResult.success(enabled ? "同步已开启" : "同步已关闭");
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("切换同步状态失败", e);
            return ApiResult.fail("操作失败: " + e.getMessage());
        }
    }

    /**
     * 查询同步状态
     */
    @GetMapping("/status")
    public ApiResult<Map<String, Object>> getSyncStatus(@RequestParam("emailAccountId") String emailAccountId) {
        try {
            String userId = SecurityContext.getCurrentUser().getUserId();
            if (Strings.isBlank(userId)) {
                return ApiResult.fail("用户未登录");
            }
            Map<String, Object> status = emailSyncService.getSyncStatus(userId, emailAccountId);
            return ApiResult.success(status);
        } catch (Exception e) {
            log.error("查询同步状态失败", e);
            return ApiResult.fail("查询失败: " + e.getMessage());
        }
    }

    /**
     * 查询同步进度（已同步数量、远端总数、文件夹级日志）
     */
    @GetMapping("/progress")
    public ApiResult<Map<String, Object>> getSyncProgress(@RequestParam("emailAccountId") String emailAccountId) {
        try {
            String userId = SecurityContext.getCurrentUser().getUserId();
            if (Strings.isBlank(userId)) {
                return ApiResult.fail("用户未登录");
            }
            Map<String, Object> progress = emailSyncService.getSyncProgress(userId, emailAccountId);
            return ApiResult.success(progress);
        } catch (Exception e) {
            log.error("查询同步进度失败", e);
            return ApiResult.fail("查询失败: " + e.getMessage());
        }
    }

    /**
     * 查询同步日志
     */
    @GetMapping("/log")
    public ApiResult<List<Map<String, Object>>> getSyncLogs(
            @RequestParam("emailAccountId") String emailAccountId,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        try {
            List<Map<String, Object>> logs = emailSyncService.getSyncLogs(emailAccountId, limit);
            return ApiResult.success(logs);
        } catch (Exception e) {
            log.error("查询同步日志失败", e);
            return ApiResult.fail("查询失败: " + e.getMessage());
        }
    }

    private boolean parseBoolean(Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        String s = String.valueOf(v).trim().toLowerCase();
        return "true".equals(s) || "1".equals(s) || "yes".equals(s);
    }
}

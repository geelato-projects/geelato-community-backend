package cn.geelato.web.platform.srv.file;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.meta.Attachment;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.common.OSSFileHelper;
import cn.geelato.web.platform.handler.FileHandler;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.oss.OSSResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.*;
import java.util.concurrent.*;

/**
 * OSS 存储管理控制器
 */
@ApiRestController("/oss")
@ConditionalOnBean(OSSFileHelper.class)
@Slf4j
public class OssController extends BaseController {

    private final OSSFileHelper fileHelper;
    private final FileHandler fileHandler;

    /**
     * 核对任务状态
     */
    private static class VerifyTask {
        volatile String status = "running"; // running | completed | failed
        volatile int total = 0;
        volatile int checked = 0;
        volatile int existCount = 0;
        volatile int missingCount = 0;
        final List<Map<String, Object>> results = Collections.synchronizedList(new ArrayList<>());
        volatile String errorMessage;
    }

    private final Map<String, VerifyTask> verifyTasks = new ConcurrentHashMap<>();
    private final ExecutorService verifyExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "oss-verify");
        t.setDaemon(true);
        return t;
    });

    @Autowired
    public OssController(OSSFileHelper fileHelper, FileHandler fileHandler) {
        this.fileHelper = fileHelper;
        this.fileHandler = fileHandler;
    }

    /**
     * 获取 OSS Bucket 统计信息（存储占用、文件数量等）
     */
    @RequestMapping(value = "/stats", method = RequestMethod.GET)
    public ApiResult<?> stats() {
        OSSResult ossResult = fileHelper.getBucketStats();
        if (Boolean.TRUE.equals(ossResult.getSuccess())) {
            return ApiResult.success(ossResult.getBucketStats());
        } else {
            return ApiResult.fail(ossResult.getMessage() != null ? ossResult.getMessage() : "获取 OSS 统计信息失败");
        }
    }

    /**
     * 启动双边核对任务（异步）
     * 返回 taskId，前端通过 /api/oss/verify-status/{taskId} 轮询进度
     */
    @RequestMapping(value = "/start-verify", method = RequestMethod.POST)
    public ApiResult<?> startVerify() {
        String taskId = UUID.randomUUID().toString();
        VerifyTask task = new VerifyTask();
        verifyTasks.put(taskId, task);

        verifyExecutor.submit(() -> executeVerify(task));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("taskId", taskId);
        return ApiResult.success(response);
    }

    /**
     * 查询核对任务状态
     */
    @RequestMapping(value = "/verify-status/{taskId}", method = RequestMethod.GET)
    public ApiResult<?> verifyStatus(@PathVariable String taskId) {
        VerifyTask task = verifyTasks.get(taskId);
        if (task == null) {
            return ApiResult.fail("任务不存在或已过期");
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", task.status);
        response.put("total", task.total);
        response.put("checked", task.checked);
        response.put("existCount", task.existCount);
        response.put("missingCount", task.missingCount);
        response.put("results", task.results);
        if (task.errorMessage != null) {
            response.put("errorMessage", task.errorMessage);
        }
        return ApiResult.success(response);
    }

    /**
     * 后台执行核对逻辑
     */
    private void executeVerify(VerifyTask task) {
        try {
            // Step 1: 分页获取全部 OSS 附件
            List<Attachment> allAttachments = new ArrayList<>();
            int pageSize = 200;
            long currentPage = 1;
            boolean hasMore = true;

            while (hasMore) {
                Map<String, Object> queryParams = new LinkedHashMap<>();
                queryParams.put("current", currentPage);
                queryParams.put("pageSize", pageSize);
                queryParams.put("order", "create_at|desc");
                queryParams.put("storageType", "aliyun");
                queryParams.put("startNum", (currentPage - 1) * pageSize);
                queryParams.put("orderBy", "create_at desc");

                List<Attachment> attachments = fileHandler.getAttachments(queryParams);
                if (attachments == null || attachments.isEmpty()) break;

                // 只保留有 path 的附件
                for (Attachment a : attachments) {
                    if (a.getPath() != null && !a.getPath().isBlank()) {
                        allAttachments.add(a);
                    }
                }

                long total = fileHandler.countAttachments(queryParams);
                task.total = allAttachments.size(); // 持续更新预估总数
                hasMore = currentPage * pageSize < total && attachments.size() == pageSize;
                currentPage++;
            }

            task.total = allAttachments.size();
            log.info("双边核对: 共获取 {} 个 OSS 附件", allAttachments.size());

            if (allAttachments.isEmpty()) {
                task.status = "completed";
                return;
            }

            // Step 2: 逐个校验 OSS 对象是否存在
            for (Attachment attachment : allAttachments) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("attachId", attachment.getId());
                item.put("name", attachment.getName());
                item.put("objectName", attachment.getPath());
                item.put("type", attachment.getType());

                try {
                    boolean exists = fileHelper.objectExists(attachment.getPath());
                    item.put("exists", exists);
                    if (exists) {
                        task.existCount++;
                    } else {
                        task.missingCount++;
                    }
                } catch (Exception e) {
                    log.warn("校验 OSS 对象失败: path={}, error={}", attachment.getPath(), e.getMessage());
                    item.put("exists", false);
                    item.put("error", e.getMessage());
                    task.missingCount++;
                }

                task.results.add(item);
                task.checked++;
            }

            task.status = "completed";
            log.info("双边核对完成: 总计={}, 存在={}, 缺失={}", task.total, task.existCount, task.missingCount);

        } catch (Exception e) {
            log.error("双边核对执行异常", e);
            task.status = "failed";
            task.errorMessage = e.getMessage();
        }
    }
}

package cn.geelato.web.platform.srv.file;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.common.FileHelper;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.oss.OSSResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.*;

/**
 * OSS 存储管理控制器
 */
@ApiRestController("/oss")
@Slf4j
public class OssController extends BaseController {

    private final FileHelper fileHelper;

    @Autowired
    public OssController(FileHelper fileHelper) {
        this.fileHelper = fileHelper;
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
     * 双边核对：批量检查 OSS 对象是否存在
     * 请求体：{ "objectNames": ["path1", "path2", ...] }
     * 返回：{ "results": [{ "objectName": "path1", "exists": true }, ...] }
     */
    @RequestMapping(value = "/verify", method = RequestMethod.POST)
    public ApiResult<?> verify(@RequestBody Map<String, Object> requestMap) {
        Object obj = requestMap.get("objectNames");
        if (!(obj instanceof List)) {
            return ApiResult.fail("参数 objectNames 必须为数组");
        }
        @SuppressWarnings("unchecked")
        List<String> objectNames = (List<String>) obj;
        if (objectNames.isEmpty()) {
            return ApiResult.success(Collections.emptyList());
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (String objectName : objectNames) {
            if (objectName == null || objectName.isBlank()) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("objectName", objectName);
            try {
                boolean exists = fileHelper.objectExists(objectName);
                item.put("exists", exists);
            } catch (Exception e) {
                log.warn("校验 OSS 对象失败: objectName={}, error={}", objectName, e.getMessage());
                item.put("exists", false);
                item.put("error", e.getMessage());
            }
            results.add(item);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", results.size());
        response.put("existCount", results.stream().filter(r -> Boolean.TRUE.equals(r.get("exists"))).count());
        response.put("missingCount", results.stream().filter(r -> Boolean.FALSE.equals(r.get("exists"))).count());
        response.put("results", results);
        return ApiResult.success(response);
    }
}

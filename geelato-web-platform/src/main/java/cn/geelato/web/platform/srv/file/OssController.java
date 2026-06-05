package cn.geelato.web.platform.srv.file;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.common.FileHelper;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.oss.OSSResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

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
}

package cn.geelato.archive.controller;

import cn.geelato.archive.entity.ArchivePolicy;
import cn.geelato.archive.exception.ArchiveException;
import cn.geelato.archive.service.ArchivePolicyService;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Map;

/**
 * 实体管理「开启归档」集成入口。
 * <p>实体管理侧对某实体开启归档时调用本端点：实体名 → 平台元数据解析物理表名 →
 * 插入一条默认停用的 DEFAULT 策略（后续在归档策略管理中调整/启用）。</p>
 */
@ApiRestController("/archive/entity")
@Slf4j
public class ArchiveEntityController {

    private final ArchivePolicyService policyService;

    @Autowired
    public ArchiveEntityController(ArchivePolicyService policyService) {
        this.policyService = policyService;
    }

    @RequestMapping(value = "/enable", method = RequestMethod.POST)
    public ApiResult<ArchivePolicy> enableArchive(@RequestBody Map<String, String> body) {
        try {
            String entityName = body == null ? null : body.get("entityName");
            return ApiResult.success(policyService.enableArchiveForEntity(entityName));
        } catch (ArchiveException e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }
}

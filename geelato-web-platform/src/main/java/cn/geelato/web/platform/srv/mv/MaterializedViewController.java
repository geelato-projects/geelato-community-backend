package cn.geelato.web.platform.srv.mv;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.DesignTimeApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.mv.service.MaterializedViewService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Map;

/**
 * 物化视图加速管理（引擎中立）。
 * <p>双记录架构：precheck →（可选 source/create 预建摄取通道）→ view/create 新增同名 materialized
 * 副本行（元数据加载物化优先，查询路由引擎）→ view/status 确认回填 READY → view/disable 停用
 * （软删副本行回原视图，引擎保留）→ view/drop 彻底删除。
 * <p>引擎（如 RisingWave）由连接的 db_type 决定，框架按适配器分发。
 */
@DesignTimeApiRestController("/model/mv")
@Slf4j
public class MaterializedViewController extends BaseController {

    private final MaterializedViewService materializedViewService;

    @Autowired
    public MaterializedViewController(MaterializedViewService materializedViewService) {
        this.materializedViewService = materializedViewService;
    }

    @RequestMapping(value = "/precheck", method = RequestMethod.POST)
    public ApiResult<?> precheck(@RequestBody Map<String, Object> params) {
        try {
            return ApiResult.success(materializedViewService.precheck(params));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/source/create", method = RequestMethod.POST)
    public ApiResult<?> createSource(@RequestBody Map<String, Object> params) {
        try {
            return ApiResult.success(materializedViewService.createSource(params));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    /** 预览建立物化视图的三步 DDL（不执行任何 DDL） */
    @RequestMapping(value = "/ddl/preview", method = RequestMethod.POST)
    public ApiResult<?> previewDdl(@RequestBody Map<String, Object> params) {
        try {
            return ApiResult.success(materializedViewService.previewDdl(params));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    /** 校验前端回传（可能已编辑）的三步 DDL（不执行任何 DDL） */
    @RequestMapping(value = "/ddl/validate", method = RequestMethod.POST)
    public ApiResult<?> validateDdl(@RequestBody Map<String, Object> params) {
        try {
            return ApiResult.success(materializedViewService.validateDdl(params));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    /** 单步执行：只执行三步 DDL 中的某一步（source / cdc:&lt;originTable&gt; / mv） */
    @RequestMapping(value = "/ddl/step/execute", method = RequestMethod.POST)
    public ApiResult<?> executeStep(@RequestBody Map<String, Object> params) {
        try {
            return ApiResult.success(materializedViewService.executeStep(params));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/view/create", method = RequestMethod.POST)
    public ApiResult<?> createView(@RequestBody Map<String, Object> params) {
        try {
            return ApiResult.success(materializedViewService.createView(params));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/view/status", method = RequestMethod.POST)
    public ApiResult<?> viewStatus(@RequestBody Map<String, Object> params) {
        try {
            return ApiResult.success(materializedViewService.viewStatus(params));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/view/enable", method = RequestMethod.POST)
    public ApiResult<?> enable(@RequestBody Map<String, Object> params) {
        try {
            return ApiResult.success(materializedViewService.enable(params));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/view/disable", method = RequestMethod.POST)
    public ApiResult<?> disable(@RequestBody Map<String, Object> params) {
        try {
            return ApiResult.success(materializedViewService.disable(params));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/view/refreshMeta", method = RequestMethod.POST)
    public ApiResult<?> refreshMeta(@RequestBody Map<String, Object> params) {
        try {
            return ApiResult.success(materializedViewService.refreshMeta(params));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/view/compareData", method = RequestMethod.POST)
    public ApiResult<?> compareData(@RequestBody Map<String, Object> params) {
        try {
            return ApiResult.success(materializedViewService.compareData(params));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/view/checkQuery", method = RequestMethod.POST)
    public ApiResult<?> checkQuery(@RequestBody Map<String, Object> params) {
        try {
            return ApiResult.success(materializedViewService.checkQuery(params));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/view/drop", method = RequestMethod.POST)
    public ApiResult<?> drop(@RequestBody Map<String, Object> params) {
        try {
            return ApiResult.success(materializedViewService.drop(params));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }
}

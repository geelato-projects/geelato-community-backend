package cn.geelato.web.platform.m.base.rest;


import cn.geelato.core.SessionCtx;
import cn.geelato.core.constants.MediaTypes;
import cn.geelato.lang.api.ApiMetaResult;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.base.service.MetaDdlService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Map;

/**
 * @author itechgee@126.com
 */
@ApiRestController(value = "/meta/ddl")
@Slf4j
public class MetaDdlController extends BaseController {
    private final MetaDdlService metaDdlService;

    @Autowired
    public MetaDdlController(MetaDdlService metaDdlService) {
        this.metaDdlService = metaDdlService;
    }

    /**
     * 根据实体名称重建或创建数据库表，需要切换数据库
     *
     * @param entity 实体名称，用于确定需要操作的数据库表
     * @return ApiMetaResult 表示操作结果的成功或失败
     */
    @RequestMapping(value = {"/table/{entity}"}, method = {RequestMethod.POST}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMetaResult recreate(@PathVariable("entity") String entity) {
        try {
            metaDdlService.createOrUpdateTableByEntityName(dao, entity, false);
            return ApiMetaResult.successNoResult();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            return ApiMetaResult.fail(metaDdlService.getMessage(ex));
        }
    }

    /**
     * 根据实体名称重建或创建数据库表，需要切换数据库
     *
     * @param appId 实体名称，用于确定需要操作的范围
     * @return ApiMetaResult 表示操作结果的成功或失败
     */
    @RequestMapping(value = {"/tables/{appId}"}, method = {RequestMethod.POST}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMetaResult createOrUpdateTableByAppId(@PathVariable("appId") String appId) {
        String tenantCode = SessionCtx.getCurrentTenantCode();
        return metaDdlService.createOrUpdateTableByAppId(dao, appId, tenantCode);
    }

    @RequestMapping(value = {"/views/{appId}"}, method = {RequestMethod.POST}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMetaResult createOrUpdateViewByAppId(@PathVariable("appId") String appId) {
        String tenantCode = SessionCtx.getCurrentTenantCode();
        return metaDdlService.createOrUpdateViewByAppId(dao, appId, tenantCode);
    }

    @RequestMapping(value = {"/viewOne/{id}"}, method = {RequestMethod.POST}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMetaResult createOrUpdateViewById(@PathVariable("id") String id) {
        return metaDdlService.createOrUpdateViewById(id);
    }

    /**
     * 新建或更新视图，需要切换数据库
     *
     * @param view   要创建或更新的视图名称
     * @param params 包含SQL语句的Map对象
     * @return ApiMetaResult 对象，表示操作结果
     */
    @RequestMapping(value = {"/view/{view}"}, method = {RequestMethod.POST}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMetaResult createOrUpdateViewByEntity(@PathVariable("view") String view, @RequestBody Map<String, String> params) {
        return metaDdlService.createOrUpdateViewByEntity(view, params);
    }

    /**
     * 验证视图语句
     * 代码中存在切换数据库的操作
     *
     * @param connectId 数据库连接ID
     * @param params    包含SQL语句的Map对象
     * @return ApiMetaResult 对象，表示操作结果
     */
    @RequestMapping(value = {"/view/valid/{connectId}"}, method = {RequestMethod.POST}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMetaResult<Boolean> validateView(@PathVariable("connectId") String connectId, @RequestBody Map<String, String> params) {
        try {
            boolean isValid = metaDdlService.validateViewSql(connectId, params.get("sql"));
            return ApiMetaResult.success(isValid);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiMetaResult.success(false, metaDdlService.getMessage(e));
        }
    }

    /**
     * 刷新Redis缓存
     * 仅操作元数据所在库
     *
     * @param params 包含表ID、表名称、连接ID、应用ID、租户代码的Map对象
     * @return ApiMetaResult 对象，表示操作结果
     */
    @RequestMapping(value = {"/redis/refresh"}, method = {RequestMethod.POST}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMetaResult refreshRedis(@RequestBody Map<String, String> params) {
        try {
            metaDdlService.refreshRedis(dao, params);
            return ApiMetaResult.successNoResult();
        } catch (Exception ex) {
            log.error(ex.getMessage());
            return ApiMetaResult.fail(metaDdlService.getMessage(ex));
        }
    }
}

package cn.geelato.web.platform.srv.meta;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.lang.api.ApiMetaResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.DesignTimeApiRestController;
import cn.geelato.web.common.constants.MediaTypes;
import cn.geelato.web.platform.srv.BaseController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@DesignTimeApiRestController("/meta")
@Slf4j
public class MetaController extends BaseController {

    private final MetaManager metaManager = MetaManager.singleInstance();

    /**
     * 获取数据定义信息，即元数据信�?
     *
     * @param entity 实体名称
     */
    @RequestMapping(value = {"/defined/{entity}"}, method = {RequestMethod.POST, RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMetaResult<?> defined(@PathVariable("entity") String entity) {
        if (metaManager.containsEntity(entity)) {
            return ApiMetaResult.success(metaManager.getByEntityName(entity).getAllSimpleFieldMetas());
        }
        return ApiMetaResult.fail("not found meta defined");
    }

    @RequestMapping(value = {"/fullDefined/{entity}"}, method = {RequestMethod.POST, RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMetaResult<?> fullDefined(@PathVariable("entity") String entity) {
        if (metaManager.containsEntity(entity)) {
            return ApiMetaResult.success(metaManager.getByEntityName(entity));
        }
        return ApiMetaResult.fail("not found meta defined");
    }

    /**
     * 获取实体名称列表
     */
    @RequestMapping(value = {"/entityNames"}, method = {RequestMethod.POST, RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<?> entityNames() {
        return ApiResult.success(metaManager.getAllEntityNames());
    }

    /**
     * 获取指定应用下的精简版实体元数据信息列表
     */
    @RequestMapping(value = {"/entityLiteMetas"}, method = {RequestMethod.POST, RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<?> queryLiteEntities() {
        return ApiResult.success(metaManager.getAllEntityLiteMetas());
    }


}

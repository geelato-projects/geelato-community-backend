package cn.geelato.web.platform.m.base.rest;


import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.orm.DaoException;
import cn.geelato.lang.api.ApiMetaResult;
import cn.geelato.lang.api.ApiMultiPagedResult;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.platform.annotation.ApiRuntimeRestController;
import cn.geelato.web.platform.boot.DynamicDatasourceHolder;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.utils.GqlUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;


@ApiRuntimeRestController("/bizData/")
@Slf4j
public class BizDataController extends BaseController {

    @RequestMapping(value = {"list", "list/*"}, method = {RequestMethod.POST, RequestMethod.GET})
    public ApiPagedResult list(@RequestParam(value = "withMeta", defaultValue = "true") boolean withMeta) {
        String gql = getGql("query");
        return ruleService.queryForMapList(gql, withMeta);
    }


    @RequestMapping(value = {"multiList", "multiList/*"}, method = RequestMethod.POST)
    public ApiMultiPagedResult multiList(@RequestParam(value = "withMeta", defaultValue = "true") boolean withMeta) {
        String gql = getGql(null);
        return ruleService.queryForMultiMapList(gql, withMeta);
    }


    @RequestMapping(value = {"save/{biz}"}, method = RequestMethod.POST)
    public ApiMetaResult save(@PathVariable("biz") String biz) throws DaoException {
        String gql = getGql("save");
        ApiMetaResult result = new ApiMetaResult();
        result.setData(ruleService.save(biz, gql));
        return result;
    }

    @RequestMapping(value = {"batchSave"}, method = RequestMethod.POST)
    public ApiMetaResult batchSave() throws DaoException {
        String gql = getGql("batchSave");
        ApiMetaResult result = new ApiMetaResult();
        result.setData(ruleService.batchSave(gql, true));
        return result;
    }

    @RequestMapping(value = {"multiSave"}, method = RequestMethod.POST)
    public ApiMetaResult multiSave() {
        String gql = getGql("multiSave");
        ApiMetaResult result = new ApiMetaResult();
        result.setData(ruleService.multiSave(gql));
        return result;
    }

    @RequestMapping(value = {"delete/{biz}/{id}"}, method = RequestMethod.POST)
    public ApiMetaResult delete(@PathVariable("biz") String biz, @PathVariable("id") String id) {
        ApiMetaResult result = new ApiMetaResult();
        result.setData(ruleService.delete(biz, id));
        return result;
    }

    @RequestMapping(value = {"delete2/{biz}"}, method = RequestMethod.POST)
    public ApiMetaResult delete(@PathVariable("biz") String biz) {
        String gql = getGql("delete");
        ApiMetaResult result = new ApiMetaResult();
        result.setData(ruleService.deleteByGql(biz, gql));
        return result;
    }

    @RequestMapping(value = {"tree/{biz}"}, method = RequestMethod.POST)
    public ApiResult treeNodeList(@PathVariable("biz") String biz, @RequestParam String entity, @RequestParam Long treeId) {
        return ruleService.queryForTreeNodeList(entity, treeId);
    }


    private String getGql(String type) {
        String gql = GqlUtil.resolveGql(request);
        if (type != null) {
            EntityMeta entityMeta = ruleService.resolveEntity(gql, type);
            DynamicDatasourceHolder.setDataSourceKey(entityMeta.getTableMeta().getConnectId());
        }
        return gql;
    }
}

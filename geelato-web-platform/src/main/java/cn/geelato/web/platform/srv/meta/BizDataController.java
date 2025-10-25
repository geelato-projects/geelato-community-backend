package cn.geelato.web.platform.srv.meta;


import cn.geelato.web.common.constants.MediaTypes;
import cn.geelato.core.orm.DaoException;
import cn.geelato.lang.api.ApiMetaResult;
import cn.geelato.lang.api.ApiMultiPagedResult;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRuntimeRestController;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.utils.GqlUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;


@ApiRuntimeRestController("/bizdata")
@Slf4j
public class BizDataController extends BaseController {

    @RequestMapping(value = {"/list", "list/*"}, method = {RequestMethod.POST, RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiPagedResult<?> list(@RequestParam(value = "withMeta", defaultValue = "true") boolean withMeta) {
        String gql = getGql("query");
        return ruleService.queryForMapList(gql, withMeta);
    }

    /**
     * 多列表查询，一次查询返回多个列表
     */
    @RequestMapping(value = {"/multiList", "multiList/*"}, method = RequestMethod.POST, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMultiPagedResult<?> multiList(@RequestParam(value = "withMeta", defaultValue = "true") boolean withMeta) {
        String gql = getGql(null);
        return ruleService.queryForMultiMapList(gql, withMeta);
    }

    /**
     * @param biz 业务代码
     * @return SaveResult
     */
    @RequestMapping(value = {"/save/{biz}"}, method = RequestMethod.POST, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMetaResult<?> save(@PathVariable("biz") String biz) throws DaoException {
        String gql = getGql("save");
        return ApiMetaResult.success(ruleService.save(biz, gql));
    }

    @RequestMapping(value = {"/batchSave"}, method = RequestMethod.POST, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMetaResult<?> batchSave() throws DaoException {
        String gql = getGql("batchSave");
        return ApiMetaResult.success(ruleService.batchSave(gql,true));
    }

    @RequestMapping(value = {"/multiSave"}, method = RequestMethod.POST, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMetaResult<?> multiSave() {
        String gql = getGql("multiSave");
        return ApiMetaResult.success(ruleService.multiSave(gql));
    }

    @RequestMapping(value = {"/delete/{biz}/{id}"}, method = RequestMethod.POST, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<Integer> delete(@PathVariable("biz") String biz, @PathVariable("id") String id) {
        return ApiResult.success(ruleService.delete(biz,id));
    }

    @RequestMapping(value = {"/delete2/{biz}"}, method = RequestMethod.POST, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public  ApiResult<Integer> delete(@PathVariable("biz") String biz) {
        String gql = getGql("delete2");
        return ApiResult.success(ruleService.deleteByGql(biz,gql));
    }


    @RequestMapping(value = {"/tree/{biz}"}, method = RequestMethod.POST, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<?> treeNodeList(@RequestParam String entity, @RequestParam Long treeId, @PathVariable String biz) {
        return ruleService.queryForTreeNodeList(entity, treeId);
    }


    private String getGql(String type) {
        return GqlUtil.resolveGql(this.request);
    }
}

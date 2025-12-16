package cn.geelato.web.platform.srv.meta;

import cn.geelato.test.annotation.GeelatoTest;
import cn.geelato.web.common.constants.MediaTypes;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.lang.api.ApiMetaResult;
import cn.geelato.lang.api.ApiMultiPagedResult;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.utils.GqlUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@ApiRestController("/meta")
@Slf4j
public class MetaController extends BaseController {

    private final MetaManager metaManager = MetaManager.singleInstance();


    @GeelatoTest(description = "元数据列表查询测试")
    @RequestMapping(value = {"/list", "list/*"}, method = {RequestMethod.POST, RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiPagedResult<?> list(@RequestParam(value = "withMeta", defaultValue = "true") boolean withMeta) {
        String gql = getGql("query");
        return ruleService.queryForMapList(gql, withMeta);
    }

    /**
     * 多列表查询，一次查询返回多个列表
     */
    @GeelatoTest(description = "元数据多列表查询测试")
    @RequestMapping(value = {"/multiList"}, method = RequestMethod.POST, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMultiPagedResult<?> multiList(@RequestParam(value = "withMeta", defaultValue = "true") boolean withMeta) {
        String gql = getGql(null);
        return ruleService.queryForMultiMapList(gql, withMeta);
    }

    /**
     * @param biz 业务代码
     * @return SaveResult
     */
    @GeelatoTest(description = "元数据保存测试")
    @RequestMapping(value = {"/save/{biz}"}, method = RequestMethod.POST, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMetaResult<?> save(@PathVariable("biz") String biz) {
        String gql = getGql("save");
        return ApiMetaResult.success(ruleService.save(biz, gql));
    }

    @GeelatoTest(description = "元数据批量保存测试")
    @RequestMapping(value = {"/batchSave"}, method = RequestMethod.POST, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMetaResult<?> batchSave() {
        String gql = getGql("batchSave");
        return ApiMetaResult.success(ruleService.batchSave(gql, true));
    }

    @GeelatoTest(description = "元数据多保存测试")
    @RequestMapping(value = {"/multiSave"}, method = RequestMethod.POST, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMetaResult<?> multiSave() {
        String gql = getGql("multiSave");
        return ApiMetaResult.success(ruleService.multiSave(gql));
    }

    @GeelatoTest(description = "元数据删除测试")
    @RequestMapping(value = {"/delete/{biz}/{id}"}, method = RequestMethod.POST, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<Integer> delete(@PathVariable("biz") String biz, @PathVariable("id") String id) {
        return ApiResult.success(ruleService.delete(biz, id));
    }

    @GeelatoTest(description = "元数据删除2测试")
    @RequestMapping(value = {"/delete2/{biz}"}, method = RequestMethod.POST, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<Integer> delete(@PathVariable("biz") String biz) {
        String gql = getGql("delete2");
        return ApiResult.success(ruleService.deleteByGql(biz, gql));
    }

    /**
     * 获取数据定义信息，即元数据信息
     *
     * @param entityOrQueryKey 实体名称或查询键
     */
    @GeelatoTest(description = "元数据定义查询测试")
    @RequestMapping(value = {"/defined/{entityOrQueryKey}"}, method = {RequestMethod.POST, RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMetaResult<?> defined(@PathVariable("entityOrQueryKey") String entityOrQueryKey) {
        if (metaManager.containsEntity(entityOrQueryKey)) {
            return ApiMetaResult.success(metaManager.getByEntityName(entityOrQueryKey).getAllSimpleFieldMetas());
        }
        return ApiMetaResult.fail("not found meta defined");
    }


    /**
     * 获取实体名称列表
     */
    @GeelatoTest(description = "实体名称列表查询测试")
    @RequestMapping(value = {"/entityNames"}, method = {RequestMethod.POST, RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<?> entityNames() {
        return ApiResult.success(metaManager.getAllEntityNames());
    }

    /**
     * 获取指定应用下的精简版实体元数据信息列表
     */
    @GeelatoTest(description = "精简版实体元数据查询测试")
    @RequestMapping(value = {"/entityLiteMetas"}, method = {RequestMethod.POST, RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<?> queryLiteEntities() {
        return ApiResult.success(metaManager.getAllEntityLiteMetas());
    }


    /**
     * 获取通用树数据（platform_tree_node）
     *
     * @param biz 业务代码
     * @return ApiResult
     */
    @GeelatoTest(description = "通用树数据查询测试")
    @RequestMapping(value = {"/tree/{biz}"}, method = RequestMethod.POST, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<?> treeNodeList(@RequestParam String entity, @RequestParam Long treeId, @PathVariable String biz) {
        return ruleService.queryForTreeNodeList(entity, treeId);
    }


    private String getGql(String type) {
        return GqlUtil.resolveGql(this.request);
    }

    /**
     * 唯一性校验
     */
    @GeelatoTest(description = "唯一性校验测试")
    @RequestMapping(value = {"/uniqueness"}, method = {RequestMethod.POST, RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<?> uniqueness() {
        String gql = getGql("query");
        if (Strings.isNotBlank(gql)) {
            JSONObject jo = JSON.parseObject(gql);
            String key = jo.keySet().iterator().next();
            JSONObject value = jo.getJSONObject(key);
            if (!value.containsKey("@fs")) {
                jo.getJSONObject(key).put("@fs", "id");
            }
            if (!value.containsKey("@p")) {
                jo.getJSONObject(key).put("@p", "1,10");
            }
            gql = JSON.toJSONString(jo);
        }
        ApiPagedResult<List<Map<String, Object>>> page = ruleService.queryForMapList(gql, false);
        if (page.isSuccess()) {
            return ApiResult.success(page.getTotal() == 0);
        } else {
            return ApiResult.fail(page.getMsg());
        }
    }

}

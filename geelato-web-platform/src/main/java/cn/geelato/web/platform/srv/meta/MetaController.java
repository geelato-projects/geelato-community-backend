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
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@ApiRestController("/meta")
@Slf4j
public class MetaController extends BaseController {

    private final MetaManager metaManager = MetaManager.singleInstance();


    @GeelatoTest(description = "元数据列表查询测试")
    @RequestMapping(value = {"/list", "list/*"}, method = {RequestMethod.POST, RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiPagedResult<?> list(@RequestParam(value = "withMeta", defaultValue = "true") boolean withMeta) {
        QueryPayload payload = resolveQueryPayload();
        return toApiPagedResult(ruleService.queryForMapList(payload.gql(), withMeta, payload.paramsByEntity()));
    }

    /**
     * 多列表查询，一次查询返回多个列表
     */
    @GeelatoTest(description = "元数据多列表查询测试")
    @RequestMapping(value = {"/multiList"}, method = RequestMethod.POST, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMultiPagedResult<?> multiList(@RequestParam(value = "withMeta", defaultValue = "true") boolean withMeta) {
        QueryPayload payload = resolveQueryPayload();
        return toApiMultiPagedResult(ruleService.queryForMultiMapList(payload.gql(), withMeta, payload.paramsByEntity()));
    }

    /**
     * @param biz 业务代码
     * @return SaveResult
     */
    @GeelatoTest(description = "元数据保存测试")
    @RequestMapping(value = {"/save/{biz}"}, method = RequestMethod.POST, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMetaResult<?> save(@PathVariable("biz") String biz) {
        String gql = getGql();
        return ApiMetaResult.success(ruleService.save(biz, gql));
    }

    @GeelatoTest(description = "元数据批量保存测试")
    @RequestMapping(value = {"/batchSave"}, method = RequestMethod.POST, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMetaResult<?> batchSave() {
        String gql = getGql();
        return ApiMetaResult.success(ruleService.batchSave(gql, true));
    }

    @GeelatoTest(description = "元数据多保存测试")
    @RequestMapping(value = {"/multiSave"}, method = RequestMethod.POST, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMetaResult<?> multiSave() {
        String gql = getGql();
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
        String gql = getGql();
        return ApiResult.success(ruleService.deleteByGql(biz, gql));
    }

    /**
     * 获取数据定义信息，即元数据信息
     *
     * @param entity 实体名称
     */
    @GeelatoTest(description = "元数据定义查询测试")
    @RequestMapping(value = {"/defined/{entity}"}, method = {RequestMethod.POST, RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMetaResult<?> defined(@PathVariable("entity") String entity) {
        if (metaManager.containsEntity(entity)) {
            return ApiMetaResult.success(metaManager.getByEntityName(entity).getAllSimpleFieldMetas());
        }
        return ApiMetaResult.fail("not found meta defined");
    }

    @GeelatoTest(description = "元数据定义查询测试")
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
        try {
            return ApiResult.success(ruleService.queryForTreeNodeList(entity, treeId));
        } catch (IllegalArgumentException ex) {
            return ApiResult.fail(ex.getMessage());
        }
    }


    private String getGql() {
        return GqlUtil.resolveGql(this.request);
    }

    private QueryPayload resolveQueryPayload() {
        String gql = getGql();
        if (Strings.isBlank(gql)) {
            return new QueryPayload(gql, new HashMap<>());
        }
        Map<String, Map<String, Object>> paramsByEntity = new HashMap<>();
        String trimmed = gql.trim();
        if (trimmed.startsWith("[")) {
            JSONArray root = JSON.parseArray(gql);
            for (int i = 0; i < root.size(); i++) {
                JSONObject item = root.getJSONObject(i);
                extractPf(item, paramsByEntity);
            }
            return new QueryPayload(JSON.toJSONString(root), paramsByEntity);
        } else {
            JSONObject root = JSON.parseObject(gql);
            extractPf(root, paramsByEntity);
            return new QueryPayload(JSON.toJSONString(root), paramsByEntity);
        }
    }

    private void extractPf(JSONObject root, Map<String, Map<String, Object>> paramsByEntity) {
        if (root == null || root.isEmpty()) {
            return;
        }
        root.forEach((entityName, value) -> {
            if (!(value instanceof JSONObject entityBody)) {
                return;
            }
            JSONObject pf = entityBody.getJSONObject("@pf");
            if (pf != null) {
                paramsByEntity.put(entityName, new HashMap<>(pf));
                entityBody.remove("@pf");
            }
        });
    }

    private record QueryPayload(String gql, Map<String, Map<String, Object>> paramsByEntity) {
    }

    /**
     * 唯一性校验
     */
    @GeelatoTest(description = "唯一性校验测试")
    @RequestMapping(value = {"/uniqueness"}, method = {RequestMethod.POST, RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<?> uniqueness() {
        String gql = getGql();
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
        Map<String, Object> page = ruleService.queryForMapList(gql, false);
        return ApiResult.success(getLong(page, "total") == 0);
    }

    @SuppressWarnings("unchecked")
    private ApiPagedResult<List<Map<String, Object>>> toApiPagedResult(Map<String, Object> pageData) {
        List<Map<String, Object>> data = pageData == null ? Collections.emptyList() : (List<Map<String, Object>>) pageData.getOrDefault("data", Collections.emptyList());
        ApiPagedResult<List<Map<String, Object>>> result = ApiPagedResult.success(
                data,
                getLong(pageData, "page"),
                getInt(pageData, "size"),
                getInt(pageData, "dataSize"),
                getLong(pageData, "total")
        );
        if (pageData != null && pageData.containsKey("meta")) {
            result.setMeta(pageData.get("meta"));
        }
        if (Boolean.TRUE.equals(pageData.get("cache"))) {
            result.setCache(true);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private ApiMultiPagedResult<List<Map<String, Object>>> toApiMultiPagedResult(Map<String, Object> multiPageData) {
        ApiMultiPagedResult<List<Map<String, Object>>> result = new ApiMultiPagedResult<>();
        Map<String, Map<String, Object>> dataMap = multiPageData == null
                ? Collections.emptyMap()
                : (Map<String, Map<String, Object>>) multiPageData.getOrDefault("data", Collections.emptyMap());
        Map<String, ApiMultiPagedResult.PageData<List<Map<String, Object>>>> apiDataMap = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : dataMap.entrySet()) {
            Map<String, Object> pageData = entry.getValue();
            ApiMultiPagedResult.PageData<List<Map<String, Object>>> apiPd = new ApiMultiPagedResult.PageData<>();
            apiPd.setData(pageData == null ? Collections.emptyList() : (List<Map<String, Object>>) pageData.getOrDefault("data", Collections.emptyList()));
            apiPd.setTotal(getLong(pageData, "total"));
            apiPd.setPage(getLong(pageData, "page"));
            apiPd.setSize(getInt(pageData, "size"));
            apiPd.setDataSize(getInt(pageData, "dataSize"));
            if (pageData != null && pageData.containsKey("meta")) {
                apiPd.setMeta(pageData.get("meta"));
            }
            apiDataMap.put(entry.getKey(), apiPd);
        }
        result.setData(apiDataMap);
        if (multiPageData != null && Boolean.TRUE.equals(multiPageData.get("cache"))) {
            result.setCache(true);
        }
        return result;
    }

    private long getLong(Map<String, Object> data, String key) {
        if (data == null) {
            return 0L;
        }
        Object value = data.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private int getInt(Map<String, Object> data, String key) {
        if (data == null) {
            return 0;
        }
        Object value = data.get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }

}

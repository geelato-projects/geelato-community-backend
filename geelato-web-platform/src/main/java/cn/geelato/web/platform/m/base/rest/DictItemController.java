package cn.geelato.web.platform.m.base.rest;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.base.entity.Dict;
import cn.geelato.web.platform.m.base.entity.DictItem;
import cn.geelato.web.platform.m.base.service.DictItemService;
import cn.geelato.web.platform.m.base.service.DictService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author diabl
 */
@ApiRestController("/dict/item")
@Slf4j
public class DictItemController extends BaseController {
    private static final String DICT_CODE = "dictCode";
    private static final String DICT_ID = "dictId";
    private static final String ROOT_PARENT_ID = "";
    private static final Class<DictItem> CLAZZ = DictItem.class;
    private final DictService dictService;
    private final DictItemService dictItemService;

    @Autowired
    public DictItemController(DictService dictService, DictItemService dictItemService) {
        this.dictService = dictService;
        this.dictItemService = dictItemService;
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.POST)
    public ApiPagedResult pageQuery() {
        try {
            Map<String, Object> requestBody = this.getRequestBody();
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(requestBody);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, requestBody, true);
            return dictItemService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    public ApiResult query() {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters();
            Map<String, Object> params = this.getQueryParameters(CLAZZ);
            return ApiResult.success(dictItemService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult get(@PathVariable(required = true) String id) {
        try {
            return ApiResult.success(dictItemService.getModel(CLAZZ, id));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    public ApiResult createOrUpdate(@RequestBody DictItem form) {
        try {
            // ID为空方可插入
            if (Strings.isNotBlank(form.getId())) {
                return ApiResult.success(dictItemService.updateModel(form));
            } else {
                return ApiResult.success(dictItemService.createModel(form));
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/batchCreateOrUpdate", method = RequestMethod.POST)
    public ApiResult<NullResult> batchCreateOrUpdate(@RequestBody List<DictItem> forms, String dictId, String parentId) {
        try {
            dictItemService.batchCreateOrUpdate(dictId, parentId, forms);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> isDelete(@PathVariable(required = true) String id) {
        try {
            DictItem model = dictItemService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            model.setEnableStatus(EnableStatusEnum.DISABLED.getValue());
            dictItemService.isDeleteModel(model);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/queryItemByDictCode/{dictCode}", method = RequestMethod.GET)
    public ApiResult queryItemByDictCode(@PathVariable(required = true) String dictCode) {
        List<DictItem> iResult = new ArrayList<>();
        try {
            Map<String, Object> params = new HashMap<>();
            params.put(DICT_CODE, dictCode);
            // 字典
            List<Dict> dResult = dictService.queryModel(Dict.class, params);
            // 字典项
            if (dResult != null && !dResult.isEmpty()) {
                params.remove(DICT_CODE);
                params.put(DICT_ID, dResult.get(0).getId());
                params.put(ColumnDefault.ENABLE_STATUS_FIELD, ColumnDefault.ENABLE_STATUS_VALUE);
                iResult = dictItemService.queryModel(CLAZZ, params);
            }
            return ApiResult.success(buildTree(iResult));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    public List<DictItem> buildTree(List<DictItem> pidList) {
        boolean isChild = false;
        if (pidList != null && !pidList.isEmpty()) {
            for (DictItem item : pidList) {
                if (Strings.isNotBlank(item.getPid())) {
                    isChild = true;
                } else {
                    item.setPid(ROOT_PARENT_ID);
                }
            }
        }
        if (!isChild) {
            return pidList;
        }
        Map<String, List<DictItem>> pidListMap = pidList.stream().collect(Collectors.groupingBy(DictItem::getPid));
        pidList.forEach(item -> item.setChildren(pidListMap.get(item.getId())));
        // 返回结果也改为返回顶层节点的list
        return pidListMap.get(ROOT_PARENT_ID);
    }

    @RequestMapping(value = "/validate", method = RequestMethod.POST)
    public ApiResult<Boolean> validate(@RequestBody DictItem form) {
        try {
            Map<String, String> lowers = new HashMap<>();
            lowers.put("item_code", form.getItemCode());
            Map<String, String> params = new HashMap<>();
            params.put("dict_id", form.getDictId());
            params.put("del_status", String.valueOf(ColumnDefault.DEL_STATUS_VALUE));
            params.put("tenant_code", form.getTenantCode());
            return ApiResult.success(dictService.validate("platform_dict_item", form.getId(), params, lowers));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/createDictAndItems", method = RequestMethod.POST)
    public ApiResult createDictAndItems(@RequestBody Dict form) {
        try {
            Dict dict = new Dict();
            dict.setAppId(form.getAppId());
            dict.setDictCode(form.getDictCode());
            dict.setDictName(form.getDictName());
            dict.setDictRemark(form.getDictRemark());
            dict = dictService.createModel(dict);
            if (form.getDictItems() != null && form.getDictItems().size() > 0) {
                Set<DictItem> items = new HashSet<>();
                int orderNum = 1;
                for (DictItem item : form.getDictItems()) {
                    item.setId(null);
                    item.setAppId(dict.getAppId());
                    item.setTenantCode(dict.getTenantCode());
                    item.setDictId(dict.getId());
                    item.setSeqNo(orderNum++);
                    items.add(dictItemService.createModel(item));
                }
                dict.setDictItems(items);
            }
            return ApiResult.success(dict);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/queryItemTagsByDict/{dictString}", method = RequestMethod.GET)
    public ApiResult queryItemTagsByDict(@PathVariable(required = true) String dictString) {
        Set<String> tags = new HashSet<>();
        try {
            String sql = String.format("SELECT id FROM platform_dict WHERE 1=1 AND del_status = 0 AND (id = '%s' or dict_code = '%s')", dictString, dictString);
            // 字典
            List<Map<String, Object>> dResult = dao.getJdbcTemplate().queryForList(sql);
            // 字典项
            if (dResult != null && !dResult.isEmpty()) {
                Map<String, Object> params = new HashMap<>();
                params.put(DICT_ID, dResult.get(0).get("id"));
                params.put(ColumnDefault.ENABLE_STATUS_FIELD, ColumnDefault.ENABLE_STATUS_VALUE);
                List<DictItem> iResult = dictItemService.queryModel(CLAZZ, params);
                if (iResult != null && !iResult.isEmpty()) {
                    for (DictItem item : iResult) {
                        if (Strings.isNotBlank(item.getItemTag())) {
                            tags.addAll(Arrays.asList(item.getItemTag().split(",")));
                        }
                    }
                }
            }
            return ApiResult.success(tags);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }
}

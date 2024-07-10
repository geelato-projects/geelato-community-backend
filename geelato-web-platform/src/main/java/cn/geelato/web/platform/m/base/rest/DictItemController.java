package cn.geelato.web.platform.m.base.rest;

import cn.geelato.web.platform.m.base.service.DictService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.core.api.ApiPagedResult;
import cn.geelato.core.api.ApiResult;
import cn.geelato.core.constants.ApiErrorMsg;
import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.web.platform.m.base.entity.Dict;
import cn.geelato.web.platform.m.base.entity.DictItem;
import cn.geelato.web.platform.m.base.service.DictItemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author diabl
 */
@Controller
@RequestMapping(value = "/api/dict/item")
public class DictItemController extends BaseController {
    private static final String DICT_CODE = "dictCode";
    private static final String DICT_ID = "dictId";
    private static final String ROOT_PARENT_ID = "";

    private static final Map<String, List<String>> OPERATORMAP = new LinkedHashMap<>();
    private static final Class<DictItem> CLAZZ = DictItem.class;

    static {
        OPERATORMAP.put("contains", Arrays.asList("itemCode", "itemName", "itemRemark"));
        OPERATORMAP.put("intervals", Arrays.asList("createAt", "updateAt"));
    }

    private final Logger logger = LoggerFactory.getLogger(DictItemController.class);
    @Autowired
    private DictService dictService;
    @Autowired
    private DictItemService dictItemService;

    @RequestMapping(value = "/pageQuery", method = RequestMethod.GET)
    @ResponseBody
    public ApiPagedResult pageQuery(HttpServletRequest req) {
        ApiPagedResult result = new ApiPagedResult();
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, req, OPERATORMAP);
            result = dictItemService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    @ResponseBody
    public ApiResult query(HttpServletRequest req) {
        ApiResult result = new ApiResult();
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            Map<String, Object> params = this.getQueryParameters(CLAZZ, req);
            result.setData(dictItemService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    @ResponseBody
    public ApiResult get(@PathVariable(required = true) String id) {
        ApiResult result = new ApiResult();
        try {
            result.setData(dictItemService.getModel(CLAZZ, id));
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult createOrUpdate(@RequestBody DictItem form) {
        ApiResult result = new ApiResult();
        try {
            // ID为空方可插入
            if (Strings.isNotBlank(form.getId())) {
                result.setData(dictItemService.updateModel(form));
            } else {
                result.setData(dictItemService.createModel(form));
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/batchCreateOrUpdate", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult batchCreateOrUpdate(@RequestBody List<DictItem> forms, String dictId, String parentId) {
        ApiResult result = new ApiResult();
        try {
            dictItemService.batchCreateOrUpdate(dictId, parentId, forms);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    @ResponseBody
    public ApiResult isDelete(@PathVariable(required = true) String id) {
        ApiResult result = new ApiResult();
        try {
            DictItem model = dictItemService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            model.setEnableStatus(EnableStatusEnum.DISABLED.getCode());
            dictItemService.isDeleteModel(model);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.DELETE_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/queryItemByDictCode/{dictCode}", method = RequestMethod.GET)
    @ResponseBody
    public ApiResult queryItemByDictCode(@PathVariable(required = true) String dictCode) {
        ApiResult result = new ApiResult();
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
            result.setData(buildTree(iResult));
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
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
    @ResponseBody
    public ApiResult validate(@RequestBody DictItem form) {
        ApiResult result = new ApiResult();
        try {
            Map<String, String> params = new HashMap<>();
            params.put("item_code", form.getItemCode());
            params.put("dict_id", form.getDictId());
            params.put("del_status", String.valueOf(DeleteStatusEnum.NO.getCode()));
            params.put("tenant_code", form.getTenantCode());
            result.setData(dictService.validate("platform_dict_item", form.getId(), params));
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.VALIDATE_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/createDictAndItems", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult createDictAndItems(@RequestBody Dict form) {
        ApiResult result = new ApiResult();
        try {
            Dict dict = new Dict();
            dict.setAppId(form.getAppId());
            dict.setDictCode(form.getDictCode());
            dict.setDictName(form.getDictName());
            dict.setDictRemark(form.getDictRemark());
            dict = dictService.createModel(dict);
            if (form.getDictItems() != null && form.getDictItems().size() > 0) {
                int orderNum = 1;
                for (DictItem item : form.getDictItems()) {
                    item.setId(null);
                    item.setAppId(dict.getAppId());
                    item.setTenantCode(dict.getTenantCode());
                    item.setDictId(dict.getId());
                    item.setSeqNo(orderNum++);
                    dictItemService.createModel(item);
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
        }

        return result;
    }
}
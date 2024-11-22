package cn.geelato.web.platform.m.model.rest;

import cn.geelato.core.constants.MetaDaoSql;
import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.core.meta.model.entity.TableCheck;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.model.service.DevTableCheckService;
import cn.geelato.web.platform.m.security.entity.DataItems;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.*;

@ApiRestController("/model/table/check")
@Slf4j
public class DevTableCheckController extends BaseController {
    private static final Map<String, List<String>> OPERATORMAP = new LinkedHashMap<>();
    private static final Class<TableCheck> CLAZZ = TableCheck.class;

    static {
        OPERATORMAP.put("contains", Arrays.asList("title", "code", "tableName", "columnName", "description"));
        OPERATORMAP.put("intervals", Arrays.asList("createAt", "updateAt"));
    }

    private final DevTableCheckService devTableCheckService;

    @Autowired
    public DevTableCheckController(DevTableCheckService devTableCheckService) {
        this.devTableCheckService = devTableCheckService;
    }


    @RequestMapping(value = "/pageQuery", method = RequestMethod.GET)
    public ApiPagedResult<DataItems> pageQuery() {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters();
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, OPERATORMAP);
            return devTableCheckService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    public ApiResult query() {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters();
            Map<String, Object> params = this.getQueryParameters(CLAZZ);
            return ApiResult.success(devTableCheckService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult get(@PathVariable(required = true) String id) {
        try {
            return ApiResult.success(devTableCheckService.getModel(CLAZZ, id));
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    public ApiResult createOrUpdate(@RequestBody TableCheck form) {
        try {
            devTableCheckService.afterSet(form);
            form.setSynced(false);
            if (StringUtils.isNotBlank(form.getId())) {
                return ApiResult.success(devTableCheckService.updateModel(form));
            } else {
                return ApiResult.success(devTableCheckService.createModel(form));
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> isDelete(@PathVariable(required = true) String id) {
        try {
            TableCheck model = devTableCheckService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            devTableCheckService.isDeleteModel(model);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/validate", method = RequestMethod.POST)
    public ApiResult<Boolean> validate(@RequestBody TableCheck form) {
        try {
            devTableCheckService.afterSet(form);
            if (StringUtils.isBlank(form.getTableSchema()) || StringUtils.isBlank(form.getType())) {
                throw new RuntimeException("表名或类型不能为空");
            }
            // 查询是否存在同名同类型同连接的数据检查
            Map<String, String> params = new HashMap<>();
            params.put("code", form.getCode());
            params.put("table_schema", form.getTableSchema());
            params.put("type", form.getType());
            params.put("del_status", String.valueOf(DeleteStatusEnum.NO.getCode()));
            params.put("tenant_code", form.getTenantCode());
            boolean isEmpty = devTableCheckService.validate("platform_dev_table_check", form.getId(), params);
            if (!isEmpty) {
                return ApiResult.success(false);
            }
            List<Map<String, Object>> mapList = dao.getJdbcTemplate().queryForList(
                    String.format(MetaDaoSql.SQL_QUERY_TABLE_CONSTRAINTS_BY_NAME, form.getTableSchema(), form.getType().toUpperCase(Locale.ENGLISH), form.getCode()));
            return ApiResult.success(mapList.isEmpty());
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }
}

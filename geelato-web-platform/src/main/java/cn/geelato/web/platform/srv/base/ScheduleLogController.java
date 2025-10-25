package cn.geelato.web.platform.srv.base;

import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.meta.ScheduleLog;
import cn.geelato.web.platform.srv.base.service.ScheduleLogService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;
import java.util.Map;

@ApiRestController("/schedule/log")
@Slf4j
public class ScheduleLogController extends BaseController {
    private static final Class<ScheduleLog> CLAZZ = ScheduleLog.class;
    private final ScheduleLogService scheduleLogService;

    @Autowired
    public ScheduleLogController(ScheduleLogService scheduleLogService) {
        this.scheduleLogService = scheduleLogService;
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.POST)
    public ApiPagedResult pageQuery() {
        try {
            Map<String, Object> requestBody = this.getRequestBody();
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(requestBody);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, requestBody, true);
            return scheduleLogService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    public ApiResult<List<ScheduleLog>> query() {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters();
            Map<String, Object> params = this.getQueryParameters(CLAZZ);
            List<ScheduleLog> list = scheduleLogService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy());
            return ApiResult.success(list);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult<ScheduleLog> get(@PathVariable(required = true) String id) {
        try {
            ScheduleLog model = scheduleLogService.getModel(CLAZZ, id);
            return ApiResult.success(model);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    public ApiResult<ScheduleLog> createOrUpdate(@RequestBody ScheduleLog form) {
        try {
            ScheduleLog result = new ScheduleLog();
            // ID为空方可插入
            if (Strings.isNotBlank(form.getId())) {
                result = scheduleLogService.updateModel(form);
            } else {
                result = scheduleLogService.createModel(form);
            }
            return ApiResult.success(result);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> isDelete(@PathVariable(required = true) String id) {
        try {
            ScheduleLog model = scheduleLogService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            scheduleLogService.isDeleteModel(model);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }
}

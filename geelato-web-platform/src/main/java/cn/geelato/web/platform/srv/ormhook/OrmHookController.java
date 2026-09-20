package cn.geelato.web.platform.srv.ormhook;

import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.mql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.meta.OrmHook;
import cn.geelato.meta.OrmHookLog;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.ormhook.service.OrmHookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.text.ParseException;
import java.util.List;
import java.util.Map;

/**
 * ORM 钩子 REST：钩子配置 CRUD、执行发件箱监控、死信重放。
 * <p>
 * 遵循 2026-09 控制器统一约定：不写 try-catch，异常上抛由全局异常处理器
 * （{@code PlatformExceptionHandler}）统一转 ApiResult。
 *
 * @author geelato
 */
@ApiRestController("/ormhook")
@Slf4j
public class OrmHookController extends BaseController {

    private static final Class<OrmHook> HOOK_CLAZZ = OrmHook.class;
    private static final Class<OrmHookLog> LOG_CLAZZ = OrmHookLog.class;

    private final OrmHookService ormHookService;

    @Autowired
    public OrmHookController(OrmHookService ormHookService) {
        this.ormHookService = ormHookService;
    }

    /** 钩子配置分页查询。 */
    @RequestMapping(value = "/pageQuery", method = RequestMethod.POST)
    public ApiPagedResult pageQuery() throws ParseException {
        Map<String, Object> requestBody = this.getRequestBody();
        PageQueryRequest pageQueryRequest = this.getPageQueryParameters(requestBody);
        FilterGroup filterGroup = this.getFilterGroup(HOOK_CLAZZ, requestBody, true);
        return ormHookService.pageQueryModel(HOOK_CLAZZ, filterGroup, pageQueryRequest);
    }

    /** 钩子配置列表查询（设计器实体配置页契约：GET 返回全量数组，同 /api/model/table/check/query）。 */
    @RequestMapping(value = "/query", method = RequestMethod.GET)
    public ApiResult<List<OrmHook>> query() throws ParseException {
        PageQueryRequest pageQueryRequest = this.getPageQueryParameters();
        Map<String, Object> params = this.getQueryParameters(HOOK_CLAZZ);
        List<OrmHook> list = ormHookService.queryModel(HOOK_CLAZZ, params, pageQueryRequest.getOrderBy());
        return ApiResult.success(list);
    }

    /** 单条钩子配置。 */
    @GetMapping("/get/{id}")
    public ApiResult<OrmHook> get(@PathVariable String id) {
        OrmHook model = ormHookService.getModel(HOOK_CLAZZ, id);
        Assert.notNull(model, ApiErrorMsg.IS_NULL);
        return ApiResult.success(model);
    }

    /** 创建或更新钩子配置（ID 为空插入、非空更新）；写入时硬校验 + 即时生效（刷新规则快照）。 */
    @PostMapping("/createOrUpdate")
    public ApiResult<OrmHook> createOrUpdate(@RequestBody OrmHook form) {
        if (StringUtils.isNotBlank(form.getId())) {
            form = ormHookService.updateModel(form);
        } else {
            form = ormHookService.createModel(form);
        }
        return ApiResult.success(form);
    }

    /** 逻辑删除钩子配置（同时禁用），删除后即时生效。 */
    @DeleteMapping("/isDelete/{id}")
    public ApiResult<Boolean> isDelete(@PathVariable String id) {
        OrmHook model = ormHookService.getModel(HOOK_CLAZZ, id);
        Assert.notNull(model, ApiErrorMsg.IS_NULL);
        model.setEnableStatus(EnableStatusEnum.DISABLED.getValue());
        ormHookService.isDeleteModel(model);
        return ApiResult.success(true);
    }

    /** 执行发件箱分页查询（监控）：可按 status/entityName/hookId 过滤。 */
    @RequestMapping(value = "/log/pageQuery", method = RequestMethod.POST)
    public ApiPagedResult logPageQuery() throws ParseException {
        Map<String, Object> requestBody = this.getRequestBody();
        PageQueryRequest pageQueryRequest = this.getPageQueryParameters(requestBody);
        FilterGroup filterGroup = this.getFilterGroup(LOG_CLAZZ, requestBody, true);
        return ormHookService.pageQueryModel(LOG_CLAZZ, filterGroup, pageQueryRequest);
    }

    /** 死信重放：dead → ready（重试次数清零），下一轮调度立即拾取。 */
    @PostMapping("/log/replay/{id}")
    public ApiResult<Boolean> replay(@PathVariable String id) {
        return ApiResult.success(ormHookService.replayDead(id));
    }
}

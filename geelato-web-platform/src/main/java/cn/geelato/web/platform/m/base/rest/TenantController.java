package cn.geelato.web.platform.m.base.rest;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.base.entity.Tenant;
import cn.geelato.web.platform.m.base.service.TenantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@ApiRestController("/tenant")
@Slf4j
public class TenantController extends BaseController {
    private static final Class<Tenant> CLAZZ = Tenant.class;
    private final TenantService tenantService;


    @Autowired
    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @RequestMapping(value = "/after/create/{id}", method = RequestMethod.GET)
    public ApiResult<String> afterCreate(@PathVariable() String id) {
        try {
            Tenant source = tenantService.getModel(CLAZZ, id);
            Assert.notNull(source, ApiErrorMsg.IS_NULL);
            String password = tenantService.afterCreate(source);
            return ApiResult.success(password);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/before/update/{id}", method = RequestMethod.POST)
    public ApiResult<String> beforeUpdate(@PathVariable() String id, @RequestBody Tenant target) {
        try {
            Tenant source = tenantService.getModel(CLAZZ, id);
            Assert.notNull(source, ApiErrorMsg.IS_NULL);
            String password = tenantService.beforeUpdate(source, target);
            return ApiResult.success(password);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/reset/password/{id}", method = RequestMethod.GET)
    public ApiResult<String> resetPassword(@PathVariable() String id) {
        try {
            Tenant source = tenantService.getModel(CLAZZ, id);
            String password = tenantService.resetPassword(source);
            return ApiResult.success(password);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }
}

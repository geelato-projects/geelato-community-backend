package cn.geelato.web.platform.m.syspackage.rest;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.utils.StringUtils;
import cn.geelato.utils.ZipUtils;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.handler.file.FileHandler;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.syspackage.entity.AppPackage;
import cn.geelato.web.platform.m.syspackage.entity.AppVersion;
import cn.geelato.web.platform.m.syspackage.service.AppVersionService;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * @author diabl
 */
@ApiRestController("/app/version")
@Slf4j
public class AppVersionController extends BaseController {
    private static final Class<AppVersion> CLAZZ = AppVersion.class;
    private final AppVersionService appVersionService;
    private final FileHandler fileHandler;

    @Autowired
    public AppVersionController(AppVersionService appVersionService, FileHandler fileHandler) {
        this.appVersionService = appVersionService;
        this.fileHandler = fileHandler;
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.POST)
    public ApiPagedResult pageQuery() {
        try {
            Map<String, Object> requestBody = this.getRequestBody();
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(requestBody);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, requestBody, true);
            return appVersionService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> isDelete(@PathVariable(required = true) String id) {
        try {
            AppVersion model = appVersionService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            appVersionService.isDeleteModel(model);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult<AppVersion> get(@PathVariable(required = true) String id) {
        try {
            AppVersion model = appVersionService.getModel(CLAZZ, id);
            return ApiResult.success(model);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/package/{id}", method = RequestMethod.GET)
    public ApiResult<AppPackage> getPackage(@PathVariable(required = true) String id) {
        try {
            AppVersion appVersion = appVersionService.getModel(CLAZZ, id);
            Assert.notNull(appVersion, "AppVersion does not exist");
            if (!StringUtils.isEmpty(appVersion.getPackagePath())) {
                String appPackageData = "";
                if (appVersion.getPackagePath().contains(".zgdp")) {
                    appPackageData = ZipUtils.readPackageData(appVersion.getPackagePath(), ".gdp");
                } else {
                    File file = fileHandler.toFile(appVersion.getPackagePath());
                    if (file != null) {
                        appPackageData = ZipUtils.readPackageData(file, ".gdp");
                    } else {
                        throw new RuntimeException("AppVersion package file does not exist");
                    }
                }
                AppPackage appPackage = null;
                if (!StringUtils.isEmpty(appPackageData)) {
                    appPackage = JSONObject.parseObject(appPackageData, AppPackage.class);
                    return ApiResult.success(appPackage);
                } else {
                    throw new RuntimeException("*.gdp file read failed");
                }
            } else {
                throw new RuntimeException("AppVersion package path does not exist");
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    public ApiResult<AppVersion> createOrUpdate(@RequestBody AppVersion form) {
        try {
            // ID为空方可插入
            if (Strings.isNotBlank(form.getId())) {
                return ApiResult.success(appVersionService.updateModel(form));
            } else {
                return ApiResult.success(appVersionService.createModel(form));
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/validate", method = RequestMethod.POST)
    public ApiResult<Boolean> validate(@RequestBody AppVersion form) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("version", form.getVersion());
            params.put("del_status", String.valueOf(ColumnDefault.DEL_STATUS_VALUE));
            params.put("app_id", form.getAppId());
            params.put("tenant_code", form.getTenantCode());
            return ApiResult.success(appVersionService.validate("platform_app_version", form.getId(), params));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }
}

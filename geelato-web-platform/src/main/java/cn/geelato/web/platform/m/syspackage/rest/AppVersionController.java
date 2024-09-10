package cn.geelato.web.platform.m.syspackage.rest;

import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.utils.StringUtils;
import cn.geelato.utils.ZipUtils;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.base.entity.Attach;
import cn.geelato.web.platform.m.base.rest.BaseController;
import cn.geelato.web.platform.m.base.service.AttachService;
import cn.geelato.web.platform.m.base.service.DownloadService;
import cn.geelato.web.platform.m.syspackage.entity.AppPackage;
import cn.geelato.web.platform.m.syspackage.entity.AppVersion;
import cn.geelato.web.platform.m.syspackage.service.AppVersionService;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.File;
import java.util.*;

/**
 * @author diabl
 */
@ApiRestController("/app/version")
@Slf4j
public class AppVersionController extends BaseController {
    private static final Map<String, List<String>> OPERATORMAP = new LinkedHashMap<>();
    private static final Class<AppVersion> CLAZZ = AppVersion.class;
    private static final String DEFAULT_ORDER_BY = "create_at DESC";

    static {
        OPERATORMAP.put("contains", Arrays.asList("version", "description"));
        OPERATORMAP.put("intervals", Arrays.asList("createAt", "updateAt"));
    }

    @Autowired
    private AppVersionService appVersionService;
    @Resource
    private AttachService attachService;
    @Resource
    private DownloadService downloadService;

    @RequestMapping(value = "/pageQuery", method = RequestMethod.GET)
    public ApiPagedResult pageQuery(HttpServletRequest req) {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req, DEFAULT_ORDER_BY);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, req, OPERATORMAP);
            return appVersionService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            log.error(e.getMessage());
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
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult<AppVersion> get(@PathVariable(required = true) String id) {
        try {
            AppVersion model = appVersionService.getModel(CLAZZ, id);
            return ApiResult.success(model);
        } catch (Exception e) {
            log.error(e.getMessage());
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
                    Attach attach = attachService.getModel(appVersion.getPackagePath());
                    File file = downloadService.downloadFile(attach.getName(), attach.getPath());
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
            log.error(e.getMessage());
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
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/validate", method = RequestMethod.POST)
    public ApiResult<Boolean> validate(@RequestBody AppVersion form) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("version", form.getVersion());
            params.put("del_status", String.valueOf(DeleteStatusEnum.NO.getCode()));
            params.put("app_id", form.getAppId());
            params.put("tenant_code", form.getTenantCode());
            return ApiResult.success(appVersionService.validate("platform_app_version", form.getId(), params));
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }
}

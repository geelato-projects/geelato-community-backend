package cn.geelato.web.platform.m.base.rest;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.env.EnvManager;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.DataItems;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.handler.file.FileHandler;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.base.entity.SysConfig;
import cn.geelato.web.platform.m.base.service.SysConfigService;
import cn.geelato.web.platform.m.file.entity.Attachment;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author diabl
 */
@ApiRestController("/sys/config")
@Slf4j
public class SysConfigController extends BaseController {
    private static final Class<SysConfig> CLAZZ = SysConfig.class;
    private static final String CONFIG_TYPE_UPLOAD = "UPLOAD";

    private final SysConfigService sysConfigService;
    private final FileHandler fileHandler;

    @Autowired
    public SysConfigController(SysConfigService sysConfigService, FileHandler fileHandler) {
        this.sysConfigService = sysConfigService;
        this.fileHandler = fileHandler;
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.POST)
    public ApiPagedResult pageQuery() {
        try {
            Map<String, Object> requestBody = this.getRequestBody();
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(requestBody);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, requestBody, true);
            ApiPagedResult result = sysConfigService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
            DataItems<List<SysConfig>> dataItems = (DataItems<List<SysConfig>>) result.getData();
            setConfigAssist(dataItems.getItems());
            return result;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    public ApiResult<List<SysConfig>> query() {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters();
            Map<String, Object> params = this.getQueryParameters(CLAZZ);
            List<SysConfig> list = sysConfigService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy());
            return ApiResult.success(setConfigAssist(list));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult get(@PathVariable(required = true) String id, boolean encrypt) {
        try {
            SysConfig model = sysConfigService.getModel(CLAZZ, id);
            model.afterSet();
            if (encrypt && model.isEncrypted()) {
                SysConfigService.decrypt(model);
            }
            model.setSm2Key(null);
            return ApiResult.success(model);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    public ApiResult createOrUpdate(@RequestBody SysConfig form) {
        try {
            form.afterSet();
            // ID为空方可插入
            if (Strings.isNotBlank(form.getId())) {
                form = sysConfigService.updateModel(form);
            } else {
                form = sysConfigService.createModel(form);
            }
            if (ColumnDefault.ENABLE_STATUS_VALUE == form.getEnableStatus()) {
                EnvManager.singleInstance().refreshConfig(form.getConfigKey());
            }
            return ApiResult.success(form);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> isDelete(@PathVariable(required = true) String id) {
        try {
            SysConfig model = sysConfigService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            model.setEnableStatus(EnableStatusEnum.DISABLED.getValue());
            sysConfigService.isDeleteModel(model);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/validate", method = RequestMethod.POST)
    public ApiResult<Boolean> validate(@RequestBody SysConfig form) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("config_key", form.getConfigKey());
            // params.put("app_id", form.getAppId());
            params.put("del_status", String.valueOf(ColumnDefault.DEL_STATUS_VALUE));
            params.put("tenant_code", form.getTenantCode());
            return ApiResult.success(sysConfigService.validate("platform_sys_config", form.getId(), params));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/getValue/{key}", method = RequestMethod.GET)
    public ApiResult getValue(@PathVariable(required = true) String key) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("configKey", key);
            params.put("enableStatus", ColumnDefault.ENABLE_STATUS_VALUE);
            List<SysConfig> list = sysConfigService.queryModel(CLAZZ, params);
            if (list != null && list.size() > 0) {
                return ApiResult.success(list.get(0).getConfigValue());
            }
            return ApiResult.success("");
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    private List<SysConfig> setConfigAssist(List<SysConfig> sysConfigs) {
        List<String> fileIds = new ArrayList<>();
        if (sysConfigs != null && sysConfigs.size() > 0) {
            for (SysConfig model : sysConfigs) {
                if (CONFIG_TYPE_UPLOAD.equalsIgnoreCase(model.getValueType()) && !fileIds.contains(model.getConfigValue())) {
                    fileIds.add(model.getConfigValue());
                }
            }
        }
        if (fileIds.size() > 0) {
            Map<String, Object> filter = new HashMap<>();
            filter.put("ids", String.join(",", fileIds));
            List<Attachment> attachmentList = fileHandler.getAttachments(filter);
            if (attachmentList != null && attachmentList.size() > 0) {
                for (SysConfig model : sysConfigs) {
                    if (CONFIG_TYPE_UPLOAD.equalsIgnoreCase(model.getValueType()) && Strings.isNotBlank(model.getConfigValue())) {
                        for (Attachment attachment : attachmentList) {
                            if (Strings.isNotBlank(attachment.getName()) && model.getConfigValue().equals(attachment.getId())) {
                                model.setConfigAssist(attachment.getName());
                                break;
                            }
                        }
                    }
                }
            }
        }

        return sysConfigs;
    }
}

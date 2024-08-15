package cn.geelato.web.platform.m.base.rest;

import cn.geelato.core.env.EnvManager;
import cn.geelato.web.platform.m.base.entity.Attach;
import cn.geelato.web.platform.m.base.entity.SysConfig;
import cn.geelato.web.platform.m.base.service.AttachService;
import cn.geelato.web.platform.m.base.service.SysConfigService;
import cn.geelato.web.platform.m.security.entity.DataItems;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * @author diabl
 * @date 2023/9/15 10:49
 */
@Controller
@RequestMapping(value = "/api/sys/config")
public class SysConfigController extends BaseController {
    private static final Map<String, List<String>> OPERATORMAP = new LinkedHashMap<>();
    private static final Class<SysConfig> CLAZZ = SysConfig.class;
    private static final String CONFIG_TYPE_UPLOAD = "UPLOAD";

    static {
        OPERATORMAP.put("contains", Arrays.asList("configKey", "configValue", "keyType", "remark"));
        OPERATORMAP.put("intervals", Arrays.asList("createAt", "updateAt"));
    }

    private final Logger logger = LoggerFactory.getLogger(SysConfigController.class);
    @Autowired
    private SysConfigService sysConfigService;
    @Autowired
    private AttachService attachService;

    @RequestMapping(value = "/pageQuery", method = RequestMethod.GET)
    @ResponseBody
    public ApiPagedResult pageQuery(HttpServletRequest req) {
        ApiPagedResult result = new ApiPagedResult();
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, req, OPERATORMAP);
            result = sysConfigService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
            DataItems<List<SysConfig>> dataItems = (DataItems<List<SysConfig>>) result.getData();
            setConfigAssist(dataItems.getItems());
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    @ResponseBody
    public ApiResult<List<SysConfig>> query(HttpServletRequest req) {
        ApiResult<List<SysConfig>> result = new ApiResult<>();
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            Map<String, Object> params = this.getQueryParameters(CLAZZ, req);
            List<SysConfig> list = sysConfigService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy());
            result.setData(setConfigAssist(list));
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    @ResponseBody
    public ApiResult get(@PathVariable(required = true) String id, boolean encrypt) {
        ApiResult result = new ApiResult();
        try {
            SysConfig model = sysConfigService.getModel(CLAZZ, id);
            model.afterSet();
            if (encrypt && model.isEncrypted()) {
                SysConfigService.decrypt(model);
            }
            model.setSm2Key(null);
            result.setData(model);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult createOrUpdate(@RequestBody SysConfig form) {
        ApiResult result = new ApiResult();
        try {
            form.afterSet();
            // ID为空方可插入
            if (Strings.isNotBlank(form.getId())) {
                result.setData(sysConfigService.updateModel(form));
            } else {
                result.setData(sysConfigService.createModel(form));
            }
            EnvManager.singleInstance().refreshConfig(form.getConfigKey());
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(e.getMessage());
        }

        return result;
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    @ResponseBody
    public ApiResult<SysConfig> isDelete(@PathVariable(required = true) String id) {
        ApiResult<SysConfig> result = new ApiResult<>();
        try {
            SysConfig model = sysConfigService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            model.setEnableStatus(EnableStatusEnum.DISABLED.getCode());
            sysConfigService.isDeleteModel(model);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.DELETE_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/validate", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult validate(@RequestBody SysConfig form) {
        ApiResult result = new ApiResult();
        try {
            Map<String, String> params = new HashMap<>();
            params.put("config_key", form.getConfigKey());
            params.put("app_id", form.getAppId());
            params.put("del_status", String.valueOf(DeleteStatusEnum.NO.getCode()));
            params.put("tenant_code", form.getTenantCode());
            result.setData(sysConfigService.validate("platform_sys_config", form.getId(), params));
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.VALIDATE_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/getValue/{key}", method = RequestMethod.GET)
    @ResponseBody
    public ApiResult getValue(@PathVariable(required = true) String key) {
        ApiResult result = new ApiResult();
        try {
            if (Strings.isNotBlank(key)) {
                Map<String, Object> params = new HashMap<>();
                params.put("configKey", key);
                List<SysConfig> list = sysConfigService.queryModel(CLAZZ, params);
                if (list != null && list.size() > 0) {
                    return result.setData(list.get(0).getConfigValue());
                }
            }
            result.setData("");
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
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
            List<Attach> attachList = attachService.list(filter);
            if (attachList != null && attachList.size() > 0) {
                for (SysConfig model : sysConfigs) {
                    if (CONFIG_TYPE_UPLOAD.equalsIgnoreCase(model.getValueType()) && Strings.isNotBlank(model.getConfigValue())) {
                        for (Attach attach : attachList) {
                            if (Strings.isNotBlank(attach.getName()) && model.getConfigValue().equals(attach.getId())) {
                                model.setConfigAssist(attach.getName());
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

package cn.geelato.web.platform.m.security.rest;

import cn.geelato.web.platform.m.security.entity.Encoding;
import cn.geelato.web.platform.m.security.service.EncodingService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.core.api.ApiPagedResult;
import cn.geelato.core.api.ApiResult;
import cn.geelato.core.constants.ApiErrorMsg;
import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.web.platform.m.base.rest.BaseController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * @author diabl
 * @date 2023/8/2 11:01
 */
@Controller
@RequestMapping(value = "/api/encoding")
public class EncodingController extends BaseController {
    private static final Map<String, List<String>> OPERATORMAP = new LinkedHashMap<>();
    private static final Class<Encoding> CLAZZ = Encoding.class;

    static {
        OPERATORMAP.put("contains", Arrays.asList("id", "title", "description"));
        OPERATORMAP.put("intervals", Arrays.asList("createAt", "updateAt"));
    }

    private final Logger logger = LoggerFactory.getLogger(EncodingController.class);
    @Autowired
    private EncodingService encodingService;

    @RequestMapping(value = "/pageQuery", method = RequestMethod.GET)
    @ResponseBody
    public ApiPagedResult pageQuery(HttpServletRequest req) {
        ApiPagedResult result = new ApiPagedResult();
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, req, OPERATORMAP);
            result = encodingService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
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
            result.setData(encodingService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
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
            result.setData(encodingService.getModel(CLAZZ, id));
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult createOrUpdate(@RequestBody Encoding form) {
        ApiResult result = new ApiResult();
        try {
            if (Strings.isNotBlank(form.getId())) {
                result.setData(encodingService.updateModel(form));
            } else {
                result.setData(encodingService.createModel(form));
            }
            if (result.isSuccess()) {
                encodingService.redisTemplateEncodingUpdate(form);
            }
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
            Encoding model = encodingService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            model.setEnableStatus(EnableStatusEnum.DISABLED.getCode());
            encodingService.isDeleteModel(model);
            encodingService.redisTemplateEncodingDelete(model);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.DELETE_FAIL);
        }

        return result;
    }

    /**
     * 获取一个新的编码，如需要使用系统编码app，需在参数中传入appId
     *
     * @param id                   编码ID
     * @param argumentParam        params参数
     * @param argumentBodyOptional body参数，优先级高于params参数
     * @return
     */
    @RequestMapping(value = "/generate/{id}", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult generate(@PathVariable(required = true) String id, @RequestParam Map<String, Object> argumentParam, @RequestBody Optional<Map<String, Object>> argumentBodyOptional) {
        ApiResult result = new ApiResult();
        try {
            Map<String, Object> argument = new HashMap<>();
            if (argumentParam != null && argumentParam.size() > 0) {
                argument.putAll(argumentParam);
            }
            if (!argumentBodyOptional.isEmpty()) {
                argumentBodyOptional.ifPresent(map -> {
                    map.forEach((key, value) -> argument.put(key, value));
                });
            }
            Encoding encoding = encodingService.getModel(CLAZZ, id);
            Assert.notNull(encoding, ApiErrorMsg.IS_NULL);
            result.setData(encodingService.generate(encoding, argument));
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(e.getMessage());
        }

        return result;
    }
}

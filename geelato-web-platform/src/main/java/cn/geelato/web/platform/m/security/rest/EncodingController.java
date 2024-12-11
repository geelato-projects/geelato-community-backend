package cn.geelato.web.platform.m.security.rest;

import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.security.entity.Encoding;
import cn.geelato.web.platform.m.security.service.EncodingService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * @author diabl
 */
@ApiRestController("encoding")
@Slf4j
public class EncodingController extends BaseController {
    private static final Map<String, List<String>> OPERATORMAP = new LinkedHashMap<>();
    private static final Class<Encoding> CLAZZ = Encoding.class;

    static {
        OPERATORMAP.put("contains", Arrays.asList("id", "title", "description"));
        OPERATORMAP.put("intervals", Arrays.asList("createAt", "updateAt"));
    }

    private final EncodingService encodingService;

    @Autowired
    public EncodingController(EncodingService encodingService) {
        this.encodingService = encodingService;
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.GET)
    public ApiPagedResult pageQuery() {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters();
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, OPERATORMAP);
            return encodingService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    public ApiResult query() {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters();
            Map<String, Object> params = this.getQueryParameters(CLAZZ);
            return ApiResult.success(encodingService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult get(@PathVariable(required = true) String id) {
        try {
            return ApiResult.success(encodingService.getModel(CLAZZ, id));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    public ApiResult createOrUpdate(@RequestBody Encoding form) {
        try {
            if (Strings.isNotBlank(form.getId())) {
                form = encodingService.updateModel(form);
            } else {
                form = encodingService.createModel(form);
            }
            // 更新缓存
            encodingService.redisTemplateEncodingUpdate(form);
            return ApiResult.success(form);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> isDelete(@PathVariable(required = true) String id) {
        try {
            Encoding model = encodingService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            model.setEnableStatus(EnableStatusEnum.DISABLED.getCode());
            encodingService.isDeleteModel(model);
            encodingService.redisTemplateEncodingDelete(model);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    /**
     * 获取一个新的编码
     * <p>
     * 根据提供的编码ID和参数，生成一个新的编码。如果需要使用系统编码app，需要在参数中传入appId。
     *
     * @param id                   编码ID，用于指定要生成的编码类型
     * @param argumentParam        params参数，包含生成编码所需的参数
     * @param argumentBodyOptional body参数，优先级高于params参数，包含生成编码所需的额外参数
     * @return 返回包含生成编码结果的ApiResult对象
     */
    @RequestMapping(value = "/generate/{id}", method = RequestMethod.POST)
    public ApiResult generate(@PathVariable(required = true) String id, @RequestParam Map<String, Object> argumentParam, @RequestBody Optional<Map<String, Object>> argumentBodyOptional) {
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
            return ApiResult.success(encodingService.generate(encoding, argument));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }
}

package cn.geelato.web.platform.m.zxing.reset;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.zxing.entity.Barcode;
import cn.geelato.web.platform.m.zxing.service.BarcodeService;
import cn.geelato.web.platform.m.zxing.utils.BarcodeUtils;
import cn.geelato.web.platform.m.zxing.utils.FontUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@ApiRestController(value = "/zxing/barcode")
@Slf4j
public class BarcodeController extends BaseController {
    private static final Class<Barcode> CLAZZ = Barcode.class;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BarcodeService barcodeService;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public BarcodeController(BarcodeService barcodeService) {
        this.barcodeService = barcodeService;
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.POST)
    public ApiPagedResult<?> pageQuery() {
        try {
            Map<String, Object> requestBody = this.getRequestBody();
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(requestBody);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, requestBody, true);
            return barcodeService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    public ApiResult<List<Barcode>> query() {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters();
            Map<String, Object> params = this.getQueryParameters(CLAZZ);
            return ApiResult.success(barcodeService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult<?> get(@PathVariable(required = true) String id) {
        try {
            return ApiResult.success(barcodeService.getModel(CLAZZ, id));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    public ApiResult<Barcode> createOrUpdate(@RequestBody Barcode form) {
        try {
            form.afterSet();
            // ID为空方可插入
            if (Strings.isNotBlank(form.getId())) {
                return ApiResult.success(barcodeService.updateModel(form));
            } else {
                return ApiResult.success(barcodeService.createModel(form));
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> isDelete(@PathVariable String id) {
        try {
            Barcode model = barcodeService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            barcodeService.isDeleteModel(model);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/validate", method = RequestMethod.POST)
    public ApiResult<Boolean> validate(@RequestBody Barcode form) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("code", form.getCode());
            params.put("del_status", String.valueOf(ColumnDefault.DEL_STATUS_VALUE));
            // params.put("app_id", form.getAppId());
            params.put("tenant_code", form.getTenantCode());
            return ApiResult.success(barcodeService.validate("platform_barcode", form.getId(), params));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/generate/{type}/{id}/{text}", method = RequestMethod.GET)
    public ApiResult<?> generate(@PathVariable(required = true) String type, @PathVariable(required = true) String id, @PathVariable(required = true) String text) {
        try {
            Barcode barcode = barcodeService.getModel(CLAZZ, id);
            Assert.notNull(barcode, ApiErrorMsg.IS_NULL);
            Object obj = generateByType(type, text, barcode);
            return ApiResult.success(obj);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/generate/{type}/{text}", method = RequestMethod.POST)
    public ApiResult<?> generate(@PathVariable String type, @PathVariable String text, @RequestBody Barcode form) {
        try {
            form.afterSet();
            Object obj = generateByType(type, text, form);
            return ApiResult.success(obj);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    private Object generateByType(String type, String text, Barcode barcode) throws IOException {
        Object obj;
        if ("attach".equalsIgnoreCase(type)) {
            obj = BarcodeUtils.generateBarcodeToAttach(text, barcode);
        } else if ("base64".equalsIgnoreCase(type)) {
            obj = BarcodeUtils.generateBarcodeToBase64(text, barcode);
        } else {
            obj = BarcodeUtils.generateBarcode(text, barcode);
        }
        return obj;
    }

    @RequestMapping(value = "/getFonts", method = RequestMethod.GET)
    public ApiResult<?> getFonts() {
        try {
            List<String> fonts;
            if (Boolean.TRUE.equals(redisTemplate.hasKey(FontUtils.redisTemplateKey))) {
                Object jsonObject = redisTemplate.opsForValue().get(FontUtils.redisTemplateKey);
                fonts = jsonObject != null ? objectMapper.readValue(String.valueOf(jsonObject), List.class) : new ArrayList<>();
            } else {
                fonts = FontUtils.getAvailableFontFamilies();
                String jsonText = objectMapper.writeValueAsString(fonts);
                redisTemplate.opsForValue().set(FontUtils.redisTemplateKey, jsonText);
                redisTemplate.expire(FontUtils.redisTemplateKey, 7, TimeUnit.DAYS);
            }
            return ApiResult.success(fonts);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }
}

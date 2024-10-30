package cn.geelato.web.platform.m.zxing.service;

import cn.geelato.core.SessionCtx;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.m.base.service.BaseService;
import cn.geelato.web.platform.m.zxing.entity.Barcode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class BarcodeService extends BaseService {

    public ApiResult getBarcodeByCode(String code) {
        ApiResult<Barcode> result = new ApiResult();
        boolean isCode = true;
        if (StringUtils.isNotBlank(code)) {
            try {
                Barcode barcode = getModel(Barcode.class, code);
                if (barcode != null) {
                    result.success().setData(barcode);
                    isCode = false;
                }
            } catch (Exception e) {
                isCode = true;
            }
            if (isCode) {
                try {
                    Map<String, Object> params = new HashMap<>();
                    params.put("code", code);
                    params.put("enableStatus", 1);
                    params.put("tenantCode", SessionCtx.getCurrentTenantCode());
                    List<Barcode> barcodes = queryModel(Barcode.class, params);
                    if (barcodes == null) {
                        result = ApiResult.fail("没有找到对应的条码信息");
                    } else if (barcodes.size() == 1) {
                        result = ApiResult.success(barcodes.get(0));
                    } else {
                        result = ApiResult.fail("找到多个条码信息，请联系管理员");
                    }
                } catch (Exception e) {
                    result = ApiResult.fail("没有找到对应的条码信息");
                }
            }
        } else {
            result = ApiResult.fail("条码不能为空");
        }

        return result;
    }
}

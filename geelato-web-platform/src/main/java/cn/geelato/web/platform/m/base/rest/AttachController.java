package cn.geelato.web.platform.m.base.rest;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.base.entity.Attach;
import cn.geelato.web.platform.m.base.service.AttachService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Map;

/**
 * @author diabl
 */
@ApiRestController("/attach")
@Slf4j
public class AttachController extends BaseController {
    private final AttachService attachService;

    @Autowired
    public AttachController(AttachService attachService) {
        this.attachService = attachService;
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult get(@PathVariable(required = true) String id) {
        try {
            return ApiResult.success(attachService.getModel(id));
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/list", method = RequestMethod.POST)
    public ApiResult list(@RequestBody Map<String, Object> requestMap) {
        try {
            if (requestMap != null && requestMap.size() > 0) {
                return ApiResult.success(attachService.list(requestMap));
            } else {
                return ApiResult.fail("Params is null");
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/remove/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> remove(@PathVariable(required = true) String id, Boolean isRemoved) {
        try {
            Attach model = attachService.getModel(id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            attachService.isDeleteModel(model);
            if (isRemoved) {
                boolean delFile = attachService.deleteFile(model);
                if (!delFile) {
                    return ApiResult.fail("File delete fail");
                }
            }
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }
}

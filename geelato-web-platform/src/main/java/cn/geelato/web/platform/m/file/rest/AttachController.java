package cn.geelato.web.platform.m.file.rest;

import cn.geelato.core.SessionCtx;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.utils.DateUtils;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.handler.file.FileHandler;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.file.entity.Attachment;
import cn.geelato.web.platform.m.file.enums.AttachmentSourceEnum;
import cn.geelato.web.platform.m.file.handler.AccessoryHandler;
import cn.geelato.web.platform.m.file.param.FileParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author diabl
 */
@ApiRestController("/attach")
@Slf4j
public class AttachController extends BaseController {
    private static final SimpleDateFormat sdf = new SimpleDateFormat(DateUtils.DATETIME);
    private final AccessoryHandler accessoryHandler;
    private final FileHandler fileHandler;

    @Autowired
    public AttachController(AccessoryHandler accessoryHandler, FileHandler fileHandler) {
        this.accessoryHandler = accessoryHandler;
        this.fileHandler = fileHandler;
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult get(@PathVariable(required = true) String id) {
        Attachment attachment = accessoryHandler.getAttachment(id, false);
        return ApiResult.success(attachment);
    }

    @RequestMapping(value = "/list", method = RequestMethod.POST)
    public ApiResult list(@RequestBody Map<String, Object> requestMap) {
        List<Attachment> attachments = accessoryHandler.getAttachments(requestMap);
        return ApiResult.success(attachments);
    }

    @RequestMapping(value = "/remove/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> remove(@PathVariable(required = true) String id, Boolean isRemoved) {
        accessoryHandler.delete(id, isRemoved);
        return ApiResult.successNoResult();
    }

    @RequestMapping(value = "/compress", method = RequestMethod.POST)
    public ApiResult compress(@RequestBody Map<String, Object> params) throws IOException, ParseException {
        String serviceType = params.get("serviceType") == null ? null : params.get("serviceType").toString();
        Date invalidTime = params.get("invalidTime") == null ? null : sdf.parse(params.get("invalidTime").toString());
        Integer amount = params.get("amount") == null ? null : Integer.parseInt(params.get("amount").toString());
        String fileName = params.get("fileName") == null ? null : params.get("fileName").toString();
        String attachmentIds = params.get("attachmentIds") == null ? null : params.get("attachmentIds").toString();
        FileParam fileParam = new FileParam(serviceType, AttachmentSourceEnum.PLATFORM_COMPRESS.getValue(), null, null, "ZIP", invalidTime, getAppId(), SessionCtx.getCurrentTenantCode());
        List<Attachment> attachments = fileHandler.compress(attachmentIds, fileName, amount, fileParam);
        return ApiResult.success(attachments);
    }
}

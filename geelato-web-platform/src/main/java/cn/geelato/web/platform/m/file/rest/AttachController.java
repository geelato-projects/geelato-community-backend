package cn.geelato.web.platform.m.file.rest;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.handler.file.FileHandler;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.file.entity.Attachment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author diabl
 */
@ApiRestController("/attach")
@Slf4j
public class AttachController extends BaseController {
    private final FileHandler fileHandler;

    @Autowired
    public AttachController(FileHandler fileHandler) {
        this.fileHandler = fileHandler;
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult get(@PathVariable(required = true) String id) {
        Attachment attachment = fileHandler.getAttachment(id, false);
        return ApiResult.success(attachment);
    }

    @RequestMapping(value = "/image/{id}", method = RequestMethod.POST)
    public ApiResult image(@PathVariable(required = true) String id) {
        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("pids", id);
        List<Attachment> attachmentList = fileHandler.getAttachments(queryParams);
        return ApiResult.success(attachmentList);
    }

    @RequestMapping(value = "/list", method = RequestMethod.POST)
    public ApiResult list(@RequestBody Map<String, Object> requestMap) {
        List<Attachment> attachments = fileHandler.getAttachments(requestMap);
        return ApiResult.success(attachments);
    }

    @RequestMapping(value = "/remove/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> remove(@PathVariable(required = true) String id, Boolean isRemoved) {
        fileHandler.delete(id, isRemoved);
        return ApiResult.successNoResult();
    }
}

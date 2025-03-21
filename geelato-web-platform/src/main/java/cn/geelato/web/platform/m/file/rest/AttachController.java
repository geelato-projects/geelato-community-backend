package cn.geelato.web.platform.m.file.rest;

import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.DataItems;
import cn.geelato.lang.api.NullResult;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.handler.file.FileHandler;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.file.entity.Attachment;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

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

    @RequestMapping(value = "/update/{id}", method = RequestMethod.POST)
    public ApiResult update(@PathVariable(required = true) String id, @RequestBody Map<String, Object> requestMap) throws InvocationTargetException, IllegalAccessException {
        Attachment attachment = fileHandler.getAttachment(id);
        BeanUtils.populate(attachment, requestMap);
        fileHandler.updateAttachment(attachment);
        return ApiResult.success(attachment);
    }

    @RequestMapping(value = "/list", method = RequestMethod.POST)
    public ApiResult list(@RequestBody Map<String, Object> requestMap) {
        List<Attachment> attachments = fileHandler.getAttachments(requestMap);
        return ApiResult.success(attachments);
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.POST)
    public ApiPagedResult pageQuery(@RequestBody Map<String, Object> requestMap) {
        long page = NumberUtils.toLong(Objects.toString(requestMap.get("current"), ""), 1L);
        int size = NumberUtils.toInt(Objects.toString(requestMap.get("pageSize"), ""), 10);
        String orderBy = Objects.toString(requestMap.get("order"), "");
        requestMap.put("orderBy", Strings.isNotBlank(orderBy) ? orderBy.replaceAll("\\|", " ") : "create_at desc");
        requestMap.put("startNum", (page - 1) * size);
        List<Attachment> attachments = fileHandler.getAttachments(requestMap);
        long total = fileHandler.countAttachments(requestMap);
        return ApiPagedResult.success(new DataItems<>(attachments, total), page, size, size, total);
    }

    @RequestMapping(value = "/column/{type}", method = RequestMethod.GET)
    public ApiResult columnType(@PathVariable(required = true) String type) {
        List<String> typeValues = new ArrayList<>();
        if (List.of("type", "resolution").contains(type)) {
            List<Map<String, Object>> data = dao.queryForMapList("platform_attachment_query_" + type, new HashMap<>());
            if (data != null && !data.isEmpty()) {
                for (Map<String, Object> map : data) {
                    String value = map.get(type) == null ? "" : map.get(type).toString();
                    if (Strings.isNotBlank(value)) {
                        typeValues.add(value);
                    }
                }
            }
        }
        return ApiResult.success(typeValues);
    }

    @RequestMapping(value = "/remove/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> remove(@PathVariable(required = true) String id, Boolean isRemoved) {
        fileHandler.delete(id, isRemoved);
        return ApiResult.successNoResult();
    }
}

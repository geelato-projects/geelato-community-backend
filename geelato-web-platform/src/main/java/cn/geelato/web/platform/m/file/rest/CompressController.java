package cn.geelato.web.platform.m.file.rest;

import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.utils.DateUtils;
import cn.geelato.utils.SqlParams;
import cn.geelato.utils.StringUtils;
import cn.geelato.utils.enums.TimeUnitEnum;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.boot.DynamicDatasourceHolder;
import cn.geelato.web.platform.handler.file.FileHandler;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.file.entity.Attachment;
import cn.geelato.web.platform.m.file.param.CompressRequestBody;
import cn.geelato.web.platform.m.file.param.FileParam;
import cn.geelato.web.platform.m.file.utils.FileParamUtils;
import cn.geelato.web.platform.utils.GqlResolveException;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@ApiRestController("/pack")
@Slf4j
public class CompressController extends BaseController {
    private final FileHandler fileHandler;

    @Autowired
    public CompressController(FileHandler fileHandler) {
        this.fileHandler = fileHandler;
    }

    @RequestMapping(value = "/by/fileIds", method = RequestMethod.POST)
    public ApiResult byFileIds(@RequestBody Map<String, Object> params) throws IOException {
        CompressRequestBody compBody = JSON.parseObject(JSON.toJSONString(params), CompressRequestBody.class);
        return build(compBody);
    }

    @RequestMapping(value = "/by/batch", method = RequestMethod.POST)
    public ApiResult byBatchNos(@RequestBody Map<String, Object> params) throws IOException {
        CompressRequestBody compBody = JSON.parseObject(JSON.toJSONString(params), CompressRequestBody.class);
        return build(compBody);
    }

    @RequestMapping(value = "/by/{type}/{column}", method = RequestMethod.POST)
    public ApiResult downloadFiles(@PathVariable(required = true) String type, @PathVariable(required = true) String column, @RequestBody Map<String, Object> params) throws Exception {
        List<String> attachmentIds = new ArrayList<>();
        // 参数
        String ids = Objects.toString(params.get("attachmentIds"), "");
        // 批次号
        String batchNoStr = "";
        if ("batchNo".equalsIgnoreCase(column)) {
            List<Attachment> attachments = fileHandler.getAttachments(SqlParams.map("ids", ids));
            if (attachments != null && !attachments.isEmpty()) {
                List<String> batchNos = attachments.stream().map(Attachment::getBatchNo).collect(Collectors.toList());
                batchNos = listStream(batchNos);
                if (batchNos == null || batchNos.isEmpty()) {
                    throw new RuntimeException("附件批次号不存在!");
                }
                batchNoStr = String.join(",", batchNos);
            }
        }
        //
        if ("pack".equalsIgnoreCase(type)) {
            CompressRequestBody compBody = new CompressRequestBody();
            compBody.setFileName(String.valueOf(System.currentTimeMillis()));
            if ("batchNo".equalsIgnoreCase(column)) {
                compBody.setBatchNos(batchNoStr);
            } else if ("id".equalsIgnoreCase(column)) {
                compBody.setAttachmentIds(ids);
            }
            String batchNo = String.valueOf(System.currentTimeMillis());
            FileParam fileParam = FileParamUtils.byBuildCompress(compBody.getServiceType(), compBody.getGenre(), compBody.getInvalidTime(), batchNo, getAppId(), getTenantCode());
            List<Attachment> attachments = fileHandler.compress(compBody.getAttachmentIds(), compBody.getBatchNos(), compBody.getFileName(), compBody.getAmount(), fileParam);
            if (attachments == null || attachments.isEmpty()) {
                throw new RuntimeException("压缩失败!");
            }
            attachmentIds = attachments.stream().map(Attachment::getId).collect(Collectors.toList());
        } else if ("simple".equalsIgnoreCase(type)) {
            if ("batchNo".equalsIgnoreCase(column)) {
                List<Attachment> attachments = fileHandler.getAttachments(SqlParams.map("batchNo", batchNoStr));
                if (attachments != null && !attachments.isEmpty()) {
                    attachmentIds = attachments.stream().map(Attachment::getId).collect(Collectors.toList());
                }
            } else if ("id".equalsIgnoreCase(column)) {
                attachmentIds = StringUtils.toListDr(ids);
            }
        }
        if (attachmentIds != null && !attachmentIds.isEmpty()) {
            attachmentIds = listStream(attachmentIds);
        } else {
            throw new RuntimeException("附件不存在!");
        }
        return ApiResult.success(attachmentIds);
    }

    private List<String> listStream(List<String> list) {
        return list == null ? new ArrayList<>() : list.stream()
                .filter(s -> s != null) // 去null
                .map(String::trim)      // 去除字符串两端的空白
                .filter(s -> !s.isBlank()) // 去空字符串
                .distinct()             // 去重
                .collect(Collectors.toList());
    }

    @RequestMapping(value = "/by/thumb", method = RequestMethod.POST)
    public ApiResult byThumbs(@RequestBody Map<String, Object> params) throws IOException {
        CompressRequestBody compBody = JSON.parseObject(JSON.toJSONString(params), CompressRequestBody.class);
        compBody.setAttachmentIds(buildThumb(compBody.getAttachmentIds()));
        return build(compBody);
    }

    @RequestMapping(value = "/meta", method = RequestMethod.POST)
    public ApiResult meta(@RequestBody Map<String, Object> params) throws IOException {
        // CompressRequestBody compBody = JSON.parseObject(JSON.toJSONString(params), CompressRequestBody.class);
        // List<String> attachmentIds = getAttachmentIdsByGql(compBody.getGql());
        // return build(compBody, attachmentIds);
        return ApiResult.successNoResult();
    }

    private String buildThumb(String attachmentIds) {
        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("pids", attachmentIds);
        List<Attachment> attachmentList = fileHandler.getAttachments(queryParams);
        if (attachmentList == null || attachmentList.isEmpty()) {
            return "";
        }
        List<String> ids = attachmentList.stream().map(Attachment::getId).collect(Collectors.toList());
        return String.join(",", ids);
    }

    private ApiResult build(CompressRequestBody compBody, List<String> attachmentIds) throws IOException {
        compBody.setAttachmentIds(String.join(",", attachmentIds));
        return build(compBody);
    }

    private ApiResult build(CompressRequestBody compBody) throws IOException {
        if (compBody.getValidDuration() != null && compBody.getValidDuration().intValue() > 0) {
            Date invalidTime = DateUtils.calculateTime(compBody.getValidDuration().toString(), TimeUnitEnum.SECOND.name());
            compBody.setInvalidTime(invalidTime);
        }
        String batchNo = String.valueOf(System.currentTimeMillis());
        String appId = getAppId();
        String tenantCode = getTenantCode();
        FileParam fileParam = FileParamUtils.byBuildCompress(compBody.getServiceType(), compBody.getGenre(), compBody.getInvalidTime(), batchNo, appId, tenantCode);
        List<Attachment> attachments = fileHandler.compress(compBody.getAttachmentIds(), compBody.getBatchNos(), compBody.getFileName(), compBody.getAmount(), fileParam);
        if (attachments == null || attachments.isEmpty()) {
            return ApiResult.fail("压缩失败");
        }
        List<String> attachmentIds = attachments.stream().map(Attachment::getId).collect(Collectors.toList());
        Map<String, Object> result = new HashMap<>();
        result.put("attachmentIds", attachmentIds);
        result.put("batchNo", batchNo);
        return ApiResult.success(result);
    }

    /**
     * 通过GQL查询获取附件ID列表
     *
     * @param gql GQL查询语句
     * @return 包含所有查询到的附件ID的列表
     * @throws GqlResolveException 如果GQL查询语句为空，则抛出此异常
     */
    private List<String> getAttachmentIdsByGql(String gql) {
        List<String> attachmentIds = new ArrayList<>();
        // 检查gql是否为空
        if (StringUtils.isEmpty(gql)) {
            throw new GqlResolveException();
        }
        // 设置数据源
        EntityMeta entityMeta = ruleService.resolveEntity(gql, "query");
        DynamicDatasourceHolder.setDataSourceKey(entityMeta.getTableMeta().getConnectId());
        // 执行gql查询
        ApiPagedResult<List<Map<String, Object>>> apiPagedResult = ruleService.queryForMapList(gql, false);
        if (apiPagedResult.isSuccess()) {
            // 获取查询结果中的附件ID字段值，并添加到attachmentIds列表中
            List<Map<String, Object>> mapList = apiPagedResult.getData();
            if (mapList != null && !mapList.isEmpty()) {
                for (Map<String, Object> map : mapList) {
                    // 取第一个字段的值作为附件ID
                    for (Map.Entry<String, Object> entry : map.entrySet()) {
                        String value = entry.getValue() == null ? "" : entry.getValue().toString();
                        if (Strings.isNotBlank(value) && !attachmentIds.contains(value)) {
                            attachmentIds.add(value);
                        }
                        break;
                    }
                }
            }
        }
        return attachmentIds;
    }
}

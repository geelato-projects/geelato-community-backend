package cn.geelato.web.platform.m.file.rest;

import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.boot.DynamicDatasourceHolder;
import cn.geelato.web.platform.handler.file.FileHandler;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.file.entity.Attachment;
import cn.geelato.web.platform.m.file.param.CompressRequestBody;
import cn.geelato.web.platform.m.file.param.FileParam;
import cn.geelato.web.platform.m.file.service.CompressService;
import cn.geelato.web.platform.m.file.utils.FileParamUtils;
import cn.geelato.web.platform.utils.GqlResolveException;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApiRestController("/compress")
@Slf4j
public class CompressController extends BaseController {
    private final CompressService compressService;
    private final FileHandler fileHandler;

    @Autowired
    public CompressController(CompressService compressService, FileHandler fileHandler) {
        this.compressService = compressService;
        this.fileHandler = fileHandler;
    }

    @RequestMapping(value = "/batch", method = RequestMethod.POST)
    public ApiResult batch(@RequestBody Map<String, Object> params) throws IOException {
        CompressRequestBody compBody = JSON.parseObject(JSON.toJSONString(params), CompressRequestBody.class);
        return build(compBody);
    }

    @RequestMapping(value = "/meta", method = RequestMethod.POST)
    public ApiResult meta(@RequestBody Map<String, Object> params) throws IOException {
        // CompressRequestBody compBody = JSON.parseObject(JSON.toJSONString(params), CompressRequestBody.class);
        // List<String> attachmentIds = getAttachmentIdsByGql(compBody.getGql());
        // return build(compBody, attachmentIds);
        return ApiResult.successNoResult();
    }

    private ApiResult<List<Attachment>> build(CompressRequestBody compBody, List<String> attachmentIds) throws IOException {
        compBody.setAttachmentIds(String.join(",", attachmentIds));
        return build(compBody);
    }

    private ApiResult<List<Attachment>> build(CompressRequestBody compBody) throws IOException {
        String batchNo = String.valueOf(System.currentTimeMillis());
        String appId = getAppId();
        String tenantCode = getTenantCode();
        FileParam fileParam = FileParamUtils.byBuildCompress(compBody.getServiceType(), compBody.getGenre(), compBody.getInvalidTime(), batchNo, appId, tenantCode);
        List<Attachment> attachments = fileHandler.compress(compBody.getAttachmentIds(), compBody.getBatchNos(), compBody.getFileName(), compBody.getAmount(), fileParam);
        return ApiResult.success(attachments);
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

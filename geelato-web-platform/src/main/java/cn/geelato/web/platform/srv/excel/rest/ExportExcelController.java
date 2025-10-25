package cn.geelato.web.platform.srv.excel.rest;

import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.handler.file.FileHandler;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.base.service.DownloadService;
import cn.geelato.web.platform.srv.excel.entity.ExportColumn;
import cn.geelato.web.platform.srv.excel.entity.PlaceholderMeta;
import cn.geelato.web.platform.srv.excel.service.ExportExcelService;
import cn.geelato.meta.Attach;
import cn.geelato.meta.Attachment;
import cn.geelato.web.platform.srv.file.enums.AttachmentSourceEnum;
import cn.geelato.web.platform.srv.file.enums.FileGenreEnum;
import cn.geelato.web.platform.srv.file.service.AttachService;
import cn.geelato.web.platform.utils.GqlUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.*;

/**
 * @author diabl
 */
@ApiRestController("/export/file")
@Slf4j
public class ExportExcelController extends BaseController {
    private static final Class<Attach> CLAZZ = Attach.class;
    private final AttachService attachService;
    private final ExportExcelService exportExcelService;
    private final DownloadService downloadService;
    private final FileHandler fileHandler;

    @Autowired
    public ExportExcelController(AttachService attachService, ExportExcelService exportExcelService, DownloadService downloadService, FileHandler fileHandler) {
        this.attachService = attachService;
        this.exportExcelService = exportExcelService;
        this.downloadService = downloadService;
        this.fileHandler = fileHandler;
    }

    @RequestMapping(value = "/list", method = RequestMethod.POST)
    public ApiPagedResult<?> pageQuery() {
        try {
            Map<String, Object> requestBody = this.getRequestBody();
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(requestBody);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, requestBody, true);
            return attachService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiPagedResult.fail(e.getMessage());
        }
    }


    /**
     * 根据模板ID导出WPS文件
     *
     * @param dataType   数据类型，支持"mql"和"data"两种类型
     * @param templateId 模板ID
     * @param fileName   文件名
     * @param markText   标记文本
     * @param markKey    标记关键字
     * @param readonly   是否只读
     * @param isDownload 是否下载文件
     * @param isPdf      是否将文件转换为PDF格式
     * @return ApiResult对象，包含操作结果
     */
    @RequestMapping(value = "/{dataType}/{templateId}", method = {RequestMethod.POST, RequestMethod.GET})
    public ApiResult<?> exportWps(@PathVariable String dataType, @PathVariable String templateId, String index, String fileName, String markText, String markKey, boolean readonly, boolean isDownload, boolean isPdf) {
        try {
            String appId = getAppId();
            // 数据解析，根据dataType的值选择不同的数据解析方式
            String jsonText = GqlUtil.resolveGql(this.request);
            List<Map> valueMapList = new ArrayList<>();
            Map valueMap = new HashMap();
            if ("mql".equals(dataType)) {
                log.info("mql");
            } else if ("data".equals(dataType) && Strings.isNotBlank(jsonText)) {
                JSONObject jo = JSON.parseObject(jsonText);
                valueMapList = (List<Map>) jo.get("valueMapList");
                valueMap = (Map) jo.get("valueMap");
            } else {
                throw new RuntimeException("Parsing this data type is not supported!");
            }
            ApiResult<?> result = exportExcelService.exportWps(appId, templateId, index, fileName, valueMapList, valueMap, markText, markKey, readonly);
            return downloadOrPdf(result, isDownload, isPdf);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        } finally {
            System.gc();
        }
    }

    /**
     * 根据传入的参数导出WPS文件。
     *
     * @param appId      应用ID
     * @param fileName   文件名
     * @param markText   标记文本
     * @param markKey    标记关键字
     * @param readonly   是否只读
     * @param isDownload 是否下载文件
     * @param isPdf      是否将文件转换为PDF格式
     * @return ApiResult对象，表示操作结果
     */
    @RequestMapping(value = "/column/meta/list", method = {RequestMethod.POST, RequestMethod.GET})
    public ApiResult<?> exportWps(String appId, String fileName, String markText, String markKey, boolean readonly, boolean isDownload, boolean isPdf) {
        try {
            List<Map> valueMapList = new ArrayList<>();
            Map valueMap = new HashMap();
            List<ExportColumn> columns = new LinkedList<>();
            List<PlaceholderMeta> metas = new LinkedList<>();

            String jsonText = GqlUtil.resolveGql(this.request);
            if (Strings.isNotBlank(jsonText)) {
                JSONObject jo = JSON.parseObject(jsonText);
                valueMapList = (List<Map>) jo.get("valueMapList");
                valueMap = (Map) jo.get("valueMap");
                columns = jo.getList("column", ExportColumn.class);
                metas = jo.getList("meta", PlaceholderMeta.class);
            }
            if (columns == null || columns.isEmpty()) {
                throw new RuntimeException("The table header of the exported template cannot be empty!");
            }
            if (metas == null || metas.isEmpty()) {
                throw new RuntimeException("The data definition information cannot be empty");
            }
            appId = Strings.isBlank(appId) ? getAppId() : appId;
            ApiResult<?> result = exportExcelService.exportExcelByColumnMeta(appId, fileName, valueMapList, valueMap, columns, metas, markText, markKey, readonly);
            return downloadOrPdf(result, isDownload, isPdf);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        } finally {
            System.gc();
        }
    }

    @RequestMapping(value = "/toPdf/{id}", method = RequestMethod.GET)
    public ApiResult<String> toPdf(@PathVariable String id) {
        try {
            java.io.File excelFile = fileHandler.toFile(id);
            if (excelFile == null || !excelFile.exists()) {
                return ApiResult.fail("文件不存在");
            }
            Attachment originalAttachment = fileHandler.getAttachment(id);
            if (originalAttachment == null) {
                return ApiResult.fail("附件信息不存在");
            }
            Attachment pdfAttachment = fileHandler.toPdf(AttachmentSourceEnum.ATTACH.getValue(), originalAttachment, FileGenreEnum.exportFile.name());
            if (pdfAttachment != null) {
                return ApiResult.success(pdfAttachment.getId());
            } else {
                return ApiResult.fail("PDF转换失败");
            }
        } catch (Exception e) {
            log.error("Excel转PDF失败: {}", e.getMessage(), e);
            return ApiResult.fail("Excel转PDF失败: " + e.getMessage());
        }
    }

    /**
     * 根据参数下载文件或将其转换为PDF格式。
     *
     * @param result     ApiResult对象，表示操作结果
     * @param isDownload 是否下载文件
     * @param isPdf      是否将文件转换为PDF格式
     * @return ApiResult对象，包含操作结果
     */
    private ApiResult<?> downloadOrPdf(ApiResult<?> result, boolean isDownload, boolean isPdf) {
        try {
            if (result.isSuccess() && result.getData() != null) {
                if (isPdf) {
                    Attachment attachment = (Attachment) result.getData();
                    result = ApiResult.success(fileHandler.toPdf(AttachmentSourceEnum.ATTACH.getValue(), attachment, FileGenreEnum.exportFile.name()));
                    // 删除原始文件
                    fileHandler.delete(attachment.getId(), true);
                }
                if (isDownload) {
                    Attachment attachment = (Attachment) result.getData();
                    downloadService.downloadFile(attachment, false, this.request, this.response, null);
                }
            }
            return result;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }
}

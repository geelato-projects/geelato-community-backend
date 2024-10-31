package cn.geelato.web.platform.m.excel.rest;

import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.base.entity.Attach;
import cn.geelato.web.platform.m.base.service.AttachService;
import cn.geelato.web.platform.m.base.service.DownloadService;
import cn.geelato.web.platform.m.excel.entity.ExportColumn;
import cn.geelato.web.platform.m.excel.entity.PlaceholderMeta;
import cn.geelato.web.platform.m.excel.service.ExportExcelService;
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
    private static final Map<String, List<String>> OPERATORMAP = new LinkedHashMap<>();
    private static final Class<Attach> CLAZZ = Attach.class;

    static {
        OPERATORMAP.put("contains", List.of("name"));
        OPERATORMAP.put("intervals", Arrays.asList("createAt", "updateAt"));
    }

    private final AttachService attachService;
    private final ExportExcelService exportExcelService;
    private final DownloadService downloadService;

    @Autowired
    public ExportExcelController(AttachService attachService, ExportExcelService exportExcelService, DownloadService downloadService) {
        this.attachService = attachService;
        this.exportExcelService = exportExcelService;
        this.downloadService = downloadService;
    }

    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public ApiPagedResult pageQuery() {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters();
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, OPERATORMAP);
            return attachService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            log.error(e.getMessage());
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
     * @throws Exception 如果在导出或转换文件过程中出现异常，则抛出该异常
     */
    @RequestMapping(value = "/{dataType}/{templateId}", method = {RequestMethod.POST, RequestMethod.GET})
    public ApiResult exportWps(@PathVariable String dataType, @PathVariable String templateId, String fileName, String markText, String markKey, boolean readonly, boolean isDownload, boolean isPdf) {
        try {
            String jsonText = GqlUtil.resolveGql(this.request);
            List<Map> valueMapList = new ArrayList<>();
            Map valueMap = new HashMap();
            if ("mql".equals(dataType)) {
            } else if ("data".equals(dataType) && Strings.isNotBlank(jsonText)) {
                JSONObject jo = JSON.parseObject(jsonText);
                valueMapList = (List<Map>) jo.get("valueMapList");
                valueMap = (Map) jo.get("valueMap");
            } else {
                throw new RuntimeException("Parsing this data type is not supported!");
            }
            ApiResult result = exportExcelService.exportWps(templateId, fileName, valueMapList, valueMap, markText, markKey, readonly);
            return downloadOrPdf(result, isDownload, isPdf);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
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
     * @throws Exception 如果在导出或转换文件过程中出现异常，则抛出该异常
     */
    @RequestMapping(value = "/column/meta/list", method = {RequestMethod.POST, RequestMethod.GET})
    public ApiResult exportWps(String appId, String fileName, String markText, String markKey, boolean readonly, boolean isDownload, boolean isPdf) {
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
            if (columns == null && columns.size() == 0) {
                throw new RuntimeException("The table header of the exported template cannot be empty!");
            }
            if (metas == null && metas.size() == 0) {
                throw new RuntimeException("The data definition information cannot be empty");
            }
            ApiResult result = exportExcelService.exportExcelByColumnMeta(appId, fileName, valueMapList, valueMap, columns, metas, markText, markKey, readonly);
            return downloadOrPdf(result, isDownload, isPdf);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    /**
     * 根据参数下载文件或将其转换为PDF格式。
     *
     * @param result     ApiResult对象，表示操作结果
     * @param isDownload 是否下载文件
     * @param isPdf      是否将文件转换为PDF格式
     * @return ApiResult对象，包含操作结果
     * @throws Exception 如果在转换或下载文件过程中出现异常，则抛出该异常
     */
    private ApiResult downloadOrPdf(ApiResult result, boolean isDownload, boolean isPdf) {
        try {
            if (result.isSuccess() && result.getData() != null) {
                if (isPdf) {
                    Attach attach = (Attach) result.getData();
                    result = ApiResult.success(downloadService.toPdfAndSave(attach));
                }
                if (isDownload) {
                    Attach attach = (Attach) result.getData();
                    downloadService.downloadFile(attach, false, this.request, this.response, null);
                }
            }
            return result;
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }
}

package cn.geelato.web.platform.m.excel.rest;

import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.base.entity.Attach;
import cn.geelato.web.platform.m.base.service.AttachService;
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

    @Autowired
    public ExportExcelController(AttachService attachService, ExportExcelService exportExcelService) {
        this.attachService = attachService;
        this.exportExcelService = exportExcelService;
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
     * 导出excel
     *
     * @param dataType   数据来源，mql、data
     * @param templateId 模板id
     * @param fileName   导出文件名称
     */
    @RequestMapping(value = "/{dataType}/{templateId}", method = {RequestMethod.POST, RequestMethod.GET})
    public ApiResult exportWps(@PathVariable String dataType, @PathVariable String templateId, String fileName, String markText, String markKey, boolean readonly) {
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
            return exportExcelService.exportWps(templateId, fileName, valueMapList, valueMap, markText, markKey, readonly);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/column/meta/list", method = {RequestMethod.POST, RequestMethod.GET})
    public ApiResult exportWps(String appId, String fileName, String markText, String markKey, boolean readonly) {
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
            return exportExcelService.exportExcelByColumnMeta(appId, fileName, valueMapList, valueMap, columns, metas, markText, markKey, readonly);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }
}

package cn.geelato.web.platform.m.excel.rest;

import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.excel.entity.ExportColumn;
import cn.geelato.web.platform.m.excel.entity.PlaceholderMeta;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.web.platform.m.base.entity.Attach;
import cn.geelato.web.platform.m.base.rest.BaseController;
import cn.geelato.web.platform.m.base.service.AttachService;
import cn.geelato.web.platform.m.excel.service.ExportExcelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.*;

/**
 * @author diabl
 */
@ApiRestController("/export/file")
public class ExportExcelController extends BaseController {
    private static final Map<String, List<String>> OPERATORMAP = new LinkedHashMap<>();
    private static final Class<Attach> CLAZZ = Attach.class;

    static {
        OPERATORMAP.put("contains", List.of("name"));
        OPERATORMAP.put("intervals", Arrays.asList("createAt", "updateAt"));
    }

    private final Logger logger = LoggerFactory.getLogger(ExportExcelController.class);
    @Autowired
    private AttachService attachService;
    @Autowired
    private ExportExcelService exportExcelService;

    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public ApiPagedResult pageQuery(HttpServletRequest req) {
        ApiPagedResult result = new ApiPagedResult();
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, req, OPERATORMAP);
            result = attachService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    /**
     * 导出excel
     *
     * @param request
     * @param response
     * @param dataType   数据来源，mql、data
     * @param templateId 模板id
     * @param fileName   导出文件名称
     */
    @RequestMapping(value = "/{dataType}/{templateId}", method = {RequestMethod.POST, RequestMethod.GET})
    public ApiResult exportWps(HttpServletRequest request, HttpServletResponse response, @PathVariable String dataType, @PathVariable String templateId,
                               String fileName, String markText, String markKey, boolean readonly) {
        ApiResult result = new ApiResult();
        try {
            String jsonText = exportExcelService.getGql(request);
            List<Map> valueMapList = new ArrayList<>();
            Map valueMap = new HashMap();
            if ("mql".equals(dataType)) {
            } else if ("data".equals(dataType) && Strings.isNotBlank(jsonText)) {
                JSONObject jo = JSON.parseObject(jsonText);
                valueMapList = (List<Map>) jo.get("valueMapList");
                valueMap = (Map) jo.get("valueMap");
            } else {
                throw new RuntimeException("暂不支持解析该数据类型！");
            }

            result = exportExcelService.exportWps(templateId, fileName, valueMapList, valueMap, markText, markKey, readonly);
        } catch (Exception e) {
            logger.error("表单信息导出Excel出错。", e);
            result.error().setMsg(e.getMessage());
        }

        return result;
    }

    @RequestMapping(value = "/column/meta/list", method = {RequestMethod.POST, RequestMethod.GET})
    public ApiResult exportWps(HttpServletRequest request, HttpServletResponse response,
                               String appId, String fileName, String markText, String markKey, boolean readonly) {
        ApiResult result = new ApiResult();
        try {
            List<Map> valueMapList = new ArrayList<>();
            Map valueMap = new HashMap();
            List<ExportColumn> columns = new LinkedList<>();
            List<PlaceholderMeta> metas = new LinkedList<>();

            String jsonText = exportExcelService.getGql(request);

            if (Strings.isNotBlank(jsonText)) {
                JSONObject jo = JSON.parseObject(jsonText);
                valueMapList = (List<Map>) jo.get("valueMapList");
                valueMap = (Map) jo.get("valueMap");
                columns = jo.getList("column", ExportColumn.class);
                metas = jo.getList("meta", PlaceholderMeta.class);
            }
            if (columns == null && columns.size() == 0) {
                return result.error().setMsg("导出模板的表头数据不能为空");
            }
            if (metas == null && metas.size() == 0) {
                return result.error().setMsg("数据定义信息不能为空");
            }
            result = exportExcelService.exportExcelByColumnMeta(appId, fileName, valueMapList, valueMap, columns, metas, markText, markKey, readonly);
        } catch (Exception e) {
            logger.error("表单信息导出Excel出错。", e);
            result.error().setMsg(e.getMessage());
        }

        return result;
    }
}

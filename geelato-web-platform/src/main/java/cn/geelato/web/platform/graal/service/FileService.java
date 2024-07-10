package cn.geelato.web.platform.graal.service;

import cn.geelato.web.platform.m.base.entity.Attach;
import cn.geelato.web.platform.m.excel.service.ExportExcelService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.core.api.ApiResult;
import cn.geelato.core.graal.GraalService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@GraalService(name = "file", built = "true")
public class FileService {

    /**
     * 返回fileId ，然后再去制定fileId下载
     *
     * @param fileName   文件名称
     * @param templateId 文件模板id
     * @param data       导出数据
     * @param options    参数
     * @return
     */
    public String exportExcel(String fileName, String templateId, Object data, Map<String, Object> options) {
        if (Strings.isBlank(fileName) || Strings.isBlank(templateId) || data == null) {
            return null;
        }
        try {
            // 业务数据
            List<Map> valueMapList = new ArrayList<>();
            Map valueMap = new HashMap();
            // 解析
            JSONObject jo = JSON.parseObject(JSON.toJSONString(data));
            valueMapList = (List<Map>) jo.get("valueMapList");
            valueMap = (Map) jo.get("valueMap");
            // 其他参数
            String markText = options.get("markText") == null ? "" : String.valueOf(options.get("markText"));
            String markKey = options.get("markKey") == null ? "" : String.valueOf(options.get("markKey"));
            Boolean readonly = options.get("readonly") == null ? false : Boolean.valueOf(String.valueOf(options.get("readonly")));
            // 导出
            ExportExcelService exportExcelService = new ExportExcelService();
            ApiResult result = exportExcelService.exportWps(templateId, fileName, valueMapList, valueMap, markText, markKey, readonly);
            if (result.isSuccess()) {
                return ((Attach) result.getData()).getId();
            }
        } catch (Exception ex) {
            return null;
        }

        return null;
    }
}

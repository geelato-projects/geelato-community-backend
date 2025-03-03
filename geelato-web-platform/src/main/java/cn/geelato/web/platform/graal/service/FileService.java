package cn.geelato.web.platform.graal.service;

import cn.geelato.core.ds.DataSourceManager;
import cn.geelato.core.graal.GraalService;
import cn.geelato.core.orm.Dao;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.utils.FileUtils;
import cn.geelato.web.platform.graal.ApplicationContextProvider;
import cn.geelato.web.platform.graal.utils.GraalUtils;
import cn.geelato.web.platform.handler.file.FileHandler;
import cn.geelato.web.platform.m.excel.service.ExportExcelService;
import cn.geelato.web.platform.m.file.entity.Attach;
import cn.geelato.web.platform.m.file.entity.Attachment;
import cn.geelato.web.platform.m.file.param.FileParam;
import cn.geelato.web.platform.m.file.utils.FileParamUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.apache.logging.log4j.util.Strings;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@GraalService(name = "file", built = "true")
public class FileService {

    private final ExportExcelService exportExcelService;
    private final FileHandler fileHandler;

    public FileService() {
        // 使用ApplicationContextProvider获取RuleService
        this.exportExcelService = ApplicationContextProvider.getBean(ExportExcelService.class);
        this.fileHandler = ApplicationContextProvider.getBean(FileHandler.class);
    }

    private Dao initDefaultDao() {
        DataSource ds = (DataSource) DataSourceManager.singleInstance().getDynamicDataSourceMap().get("primary");
        JdbcTemplate jdbcTemplate = new JdbcTemplate();
        jdbcTemplate.setDataSource(ds);
        return new Dao(jdbcTemplate);
    }

    /**
     * 导出Excel文件，并返回文件的ID。
     *
     * @param fileName   文件名称
     * @param templateId 文件模板ID
     * @param index      文件模板索引
     * @param data       导出数据
     * @param options    其他参数
     * @return 返回文件的ID，用于后续下载
     */
    public String exportExcel(String fileName, String templateId, String index, Object data, Map<String, Object> options) {
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
            boolean readonly = options.get("readonly") != null && Boolean.parseBoolean(String.valueOf(options.get("readonly")));
            // 导出
            ApiResult result = exportExcelService.exportWps(templateId, index, fileName, valueMapList, valueMap, markText, markKey, readonly);
            if (result.isSuccess()) {
                return ((Attach) result.getData()).getId();
            }
        } catch (Exception ex) {
            return null;
        }

        return null;
    }

    /**
     * 保存Base64编码的图片。
     *
     * @param base64String Base64编码的图片字符串
     * @param fileName     保存的文件名
     * @param suffix       文件的后缀名
     * @param batchNo      批次号
     * @param isThumbnail  是否生成缩略图
     * @param dimension    缩略图的尺寸，默认为100
     * @return 保存的图片的附件ID
     * @throws IOException 如果在保存文件或生成缩略图时发生I/O异常
     */
    public String saveBase64(String base64String, String fileName, String suffix, String serviceType, String tableType, String batchNo, Boolean isThumbnail, Boolean onlyThumb, String dimension) throws IOException {
        if (Strings.isBlank(base64String) || Strings.isBlank(fileName) || Strings.isBlank(suffix)) {
            throw new RuntimeException("saveBase64：base64String or fileName or suffix can not be empty");
        }
        fileName = FileUtils.getFileName(fileName) + "." + suffix;
        String tenantCode = GraalUtils.getCurrentTenantCode();
        FileParam fileParam = FileParamUtils.byBase64AndThumbnail(serviceType, tableType, "Api,Base64", batchNo, tenantCode, isThumbnail, onlyThumb, dimension);
        Attachment attachment = fileHandler.upload(base64String, fileName, fileParam);
        if (attachment == null) {
            throw new RuntimeException("saveBase64：upload file error");
        }
        return attachment.getId();
    }
}

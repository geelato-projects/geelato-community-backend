package cn.geelato.web.platform.graal.service;

import cn.geelato.core.graal.GraalService;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.platform.enums.AttachmentSourceEnum;
import cn.geelato.web.platform.graal.ApplicationContextProvider;
import cn.geelato.web.platform.graal.GraalUtils;
import cn.geelato.web.platform.m.base.entity.Attach;
import cn.geelato.web.platform.m.base.service.AttachService;
import cn.geelato.web.platform.m.base.service.UploadService;
import cn.geelato.web.platform.m.excel.service.ExportExcelService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.apache.logging.log4j.util.Strings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

@GraalService(name = "file", built = "true")
public class FileService {

    private ExportExcelService exportExcelService;
    private AttachService attachService;

    public FileService() {
        // 使用ApplicationContextProvider获取RuleService
        this.exportExcelService = ApplicationContextProvider.getBean(ExportExcelService.class);
        this.attachService = ApplicationContextProvider.getBean(AttachService.class);
    }

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
            ApiResult result = exportExcelService.exportWps(templateId, fileName, valueMapList, valueMap, markText, markKey, readonly);
            if (result.isSuccess()) {
                return ((Attach) result.getData()).getId();
            }
        } catch (Exception ex) {
            return null;
        }

        return null;
    }

    /**
     * 保存base64
     *
     * @param base64String
     * @param fileName     文件名称
     * @param suffix       文件后缀
     * @return
     */
    public String saveBase64(String base64String, String fileName, String suffix) throws IOException {
        if (Strings.isBlank(base64String) || Strings.isBlank(fileName) || Strings.isBlank(suffix)) {
            throw new RuntimeException("saveBase64：base64String or fileName or suffix can not be empty");
        }
        String[] parts = base64String.split(",");
        String base64Image = parts[parts.length - 1];
        byte[] imageBytes = Base64.getDecoder().decode(base64Image);
        fileName = fileName + "." + suffix;
        String tenantCode = GraalUtils.getCurrentTenantCode();
        String directory = UploadService.getSavePath(UploadService.ROOT_DIRECTORY, AttachmentSourceEnum.PLATFORM_ATTACH.getValue(), tenantCode, null, fileName, true);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(directory);
            fos.write(imageBytes);
            fos.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (fos != null) {
                fos.close();
            }
        }
        File file = new File(directory);
        if (!file.exists()) {
            throw new RuntimeException("saveBase64：file save failed");
        }
        Attach attach = attachService.saveByFile(file, fileName, "ApiSaveBase64", null, tenantCode);
        return attach.getId();
    }
}

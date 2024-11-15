package cn.geelato.web.platform.graal.service;

import cn.geelato.core.ds.DataSourceManager;
import cn.geelato.core.graal.GraalService;
import cn.geelato.core.orm.Dao;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.utils.ImageUtils;
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
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

@GraalService(name = "file", built = "true")
public class FileService {

    private final ExportExcelService exportExcelService;
    private final AttachService attachService;

    public FileService() {
        // 使用ApplicationContextProvider获取RuleService
        this.exportExcelService = ApplicationContextProvider.getBean(ExportExcelService.class);
        this.attachService = ApplicationContextProvider.getBean(AttachService.class);
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
     * @param data       导出数据
     * @param options    其他参数
     * @return 返回文件的ID，用于后续下载
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
            Boolean readonly = options.get("readonly") != null && Boolean.valueOf(String.valueOf(options.get("readonly")));
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
     * 保存Base64编码的图片。
     *
     * @param base64String Base64编码的图片字符串
     * @param fileName     保存的文件名
     * @param suffix       文件的后缀名
     * @return 保存的文件ID
     * @throws IOException 如果在保存文件时发生I/O异常
     */
    public String saveBase64(String base64String, String fileName, String suffix) throws IOException {
        return saveBase64(base64String, fileName, suffix, false);
    }

    /**
     * 保存Base64编码的图片，并可选择是否生成缩略图。
     *
     * @param base64String Base64编码的图片字符串
     * @param fileName     保存的文件名
     * @param suffix       文件的后缀名
     * @param isThumbnail  是否生成缩略图
     * @return 保存的文件ID
     * @throws IOException 如果在保存文件时发生I/O异常
     */
    public String saveBase64(String base64String, String fileName, String suffix, Boolean isThumbnail) throws IOException {
        return saveBase64(base64String, fileName, suffix, isThumbnail, null);
    }

    /**
     * 保存Base64编码的图片。
     *
     * @param base64String Base64编码的图片字符串
     * @param fileName     保存的文件名
     * @param suffix       文件的后缀名
     * @param isThumbnail  是否生成缩略图
     * @param dimension    缩略图的尺寸，默认为100
     * @return 保存的图片的附件ID
     * @throws IOException 如果在保存文件或生成缩略图时发生I/O异常
     */
    public String saveBase64(String base64String, String fileName, String suffix, Boolean isThumbnail, Integer dimension) throws IOException {
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
        Attach attach = attachService.saveByFile(file, fileName, "Api,SaveBase64", null, tenantCode);
        // 缩略图
        if (isThumbnail) {
            thumbnail(attach, dimension);
        }

        return attach.getId();
    }

    /**
     * 生成指定尺寸的缩略图
     *
     * @param source    源文件信息
     * @param dimension 缩略图尺寸，如果为null则默认为0
     * @throws IOException 如果在文件操作过程中发生I/O错误，则抛出此异常
     */
    private void thumbnail(Attach source, Integer dimension) throws IOException {
        File sourceFile = new File(source.getPath());
        int dv = dimension == null ? 0 : dimension;
        if (ImageUtils.isThumbnail(sourceFile, dv)) {
            String path = UploadService.getSavePath(UploadService.ROOT_DIRECTORY, AttachmentSourceEnum.PLATFORM_ATTACH.getValue(), source.getTenantCode(), source.getAppId(), source.getName(), true);
            File file = new File(path);
            ImageUtils.thumbnail(sourceFile, file, dv);
            if (!file.exists()) {
                throw new RuntimeException("saveBase64：thumbnail save failed");
            }
            Attach attach = attachService.saveByFile(file, source.getName(), source.getGenre() + "," + ImageUtils.THUMBNAIL_GENRE, source.getAppId(), source.getTenantCode());
            initDefaultDao().getJdbcTemplate().update("update platform_attach set id = ? where id = ?", source.getId() + ImageUtils.THUMBNAIL_SUFFIX, attach.getId());
        }
    }

}

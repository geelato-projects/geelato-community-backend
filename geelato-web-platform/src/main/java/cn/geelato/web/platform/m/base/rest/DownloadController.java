package cn.geelato.web.platform.m.base.rest;


import cn.geelato.core.constants.MediaTypes;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.utils.FileUtils;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.base.entity.Attach;
import cn.geelato.web.platform.m.base.service.AttachService;
import cn.geelato.web.platform.m.base.service.DownloadService;
import cn.geelato.web.platform.m.base.service.UploadService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * @author diabl
 */
@ApiRestController("/resources")
@Slf4j
public class DownloadController extends BaseController {
    // 设置不常用的媒体类型
    static final Map<String, String> EXT_MAP = new HashMap<>();

    static {
        // 前端基于esm文件打包的文件
        EXT_MAP.put("mjs", MediaTypes.APPLICATION_JAVASCRIPT);
    }

    private final DownloadService downloadService;
    private final AttachService attachService;

    @Autowired
    public DownloadController(DownloadService downloadService, AttachService attachService) {
        this.downloadService = downloadService;
        this.attachService = attachService;
    }

    @RequestMapping(value = "/file", method = RequestMethod.GET)
    public void downloadFile(String id, String name, String path, boolean isPdf, boolean isPreview, boolean isThumbnail) throws Exception {
        try {
            File file = null;
            String appId = null;
            String tenantCode = null;
            if (Strings.isNotBlank(id)) {
                Attach attach = attachService.getModelThumbnail(id, isThumbnail);
                Assert.notNull(attach, ApiErrorMsg.IS_NULL);
                appId = attach.getAppId();
                tenantCode = attach.getTenantCode();
                file = FileUtils.pathToFile(attach.getPath());
                name = attach.getName();
            } else if (Strings.isNotBlank(path)) {
                file = FileUtils.pathToFile(path);
                name = Strings.isNotBlank(name) ? name : file.getName();
            }
            if (file == null || !file.exists()) {
                throw new RuntimeException("downloadFile: file is null or not exists");
            }
            // 转为pdf文件
            if (isPdf) {
                String ext = FileUtils.getFileExtension(name);
                if (StringUtils.isBlank(ext)) {
                    throw new RuntimeException("downloadFile: invalid file extension");
                }
                name = String.format("%s.pdf", FileUtils.getFileName(name));
                file = downloadService.toPdf(file, ext, appId, tenantCode);
                if (file == null || !file.exists()) {
                    throw new RuntimeException("downloadFile: file is null or not exists");
                }
            }
            // 设置缓存
            this.response.setHeader("Cache-Control", "public, max-age=3600, must-revalidate");
            this.response.setHeader("ETag", id);
            this.response.setDateHeader("Last-Modified", file.lastModified());
            // 下载
            downloadService.downloadFile(file, name, isPreview, this.request, this.response, null);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw e;
        }
    }

    @RequestMapping(value = "/json", method = RequestMethod.GET)
    public ApiResult downloadJson(String fileName) throws IOException {
        if (Strings.isBlank(fileName)) {
            return ApiResult.fail("fileName is null");
        }
        BufferedReader bufferedReader = null;
        try {
            String ext = FileUtils.getFileExtension(fileName);
            if (Strings.isBlank(ext) || !".config".equalsIgnoreCase(ext)) {
                fileName += ".config";
            }
            File file = new File(String.format("%s/%s", UploadService.ROOT_CONFIG_DIRECTORY, fileName));
            if (!file.exists()) {
                return ApiResult.fail("File (.config) does not exist");
            }
            StringBuilder contentBuilder = new StringBuilder();
            bufferedReader = Files.newBufferedReader(Paths.get(file.getAbsolutePath()), StandardCharsets.UTF_8);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                contentBuilder.append(line);
            }
            return ApiResult.success(contentBuilder.toString());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        } finally {
            if (bufferedReader != null) {
                bufferedReader.close();
            }
        }
    }
}

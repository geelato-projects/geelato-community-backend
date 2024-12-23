package cn.geelato.web.platform.m.base.rest;


import cn.geelato.core.constants.MediaTypes;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.utils.FileUtils;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.oss.OSSResult;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.common.FileHelper;
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

import java.io.*;
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
    private final FileHelper fileHelper;

    @Autowired
    public DownloadController(DownloadService downloadService, AttachService attachService, FileHelper fileHelper) {
        this.downloadService = downloadService;
        this.attachService = attachService;
        this.fileHelper = fileHelper;
    }

    @RequestMapping(value = "/file", method = RequestMethod.GET)
    public void downloadFile(String id, String name, String path, boolean isPdf, boolean isPreview, boolean isThumbnail) throws Exception {
        File file = null;
        String appId = null;
        String tenantCode = null;
        InputStream inputStream = null;
        try {
            if (Strings.isNotBlank(id)) {
                Attach attach = attachService.getModelThumbnail(id, isThumbnail);
                Assert.notNull(attach, ApiErrorMsg.IS_NULL);
                appId = attach.getAppId();
                tenantCode = attach.getTenantCode();
                if (Strings.isNotBlank(attach.getObjectId())) {
                    OSSResult ossResult = fileHelper.getFile(attach.getPath());
                    if (ossResult.getSuccess()) {
                        inputStream = ossResult.getOssFile().getFileMeta().getFileInputStream();
                    } else {
                        throw new RuntimeException("downloadFile: ossResult is null");
                    }
                } else {
                    file = FileUtils.pathToFile(attach.getPath());
                }
                name = attach.getName();
            } else if (Strings.isNotBlank(path)) {
                file = FileUtils.pathToFile(path);
                name = Strings.isNotBlank(name) ? name : file.getName();
            }
            if (!(inputStream != null || (file != null && file.exists()))) {
                throw new RuntimeException("downloadFile: file is null or not exists");
            }
            // 转为pdf文件
            if (isPdf) {
                String ext = FileUtils.getFileExtension(name);
                if (StringUtils.isBlank(ext)) {
                    throw new RuntimeException("downloadFile: invalid file extension");
                }
                if (inputStream != null) {
                    File tempFile = File.createTempFile(name + "_temp_", ext);
                    tempFile.deleteOnExit();
                    try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = inputStream.read(buffer)) > 0) {
                            fos.write(buffer, 0, length);
                        }
                    }
                    if (tempFile.exists()) {
                        file = downloadService.toPdf(tempFile, ext, appId, tenantCode);
                        tempFile.delete();
                    } else {
                        throw new RuntimeException("downloadFile: temp file is null or not exists");
                    }
                } else {
                    file = downloadService.toPdf(file, ext, appId, tenantCode);
                }
                if (file == null || !file.exists()) {
                    throw new RuntimeException("downloadFile: file is null or not exists");
                }
                name = String.format("%s.pdf", FileUtils.getFileName(name));
            }
            // 设置缓存
            this.response.setHeader("Cache-Control", "public, max-age=3600, must-revalidate");
            this.response.setHeader("ETag", id);
            // 下载
            if (file == null && inputStream != null) {
                downloadService.downloadFile(inputStream, name, isPreview, this.request, this.response, null);
            } else {
                this.response.setDateHeader("Last-Modified", file.lastModified());
                downloadService.downloadFile(file, name, isPreview, this.request, this.response, null);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw e;
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
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

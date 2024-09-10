package cn.geelato.web.platform.m.base.rest;


import cn.geelato.core.constants.MediaTypes;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.base.entity.Attach;
import cn.geelato.web.platform.m.base.service.AttachService;
import cn.geelato.web.platform.m.base.service.DownloadService;
import cn.geelato.web.platform.m.base.service.UploadService;
import cn.geelato.web.platform.m.excel.entity.OfficeUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.*;
import java.net.URLEncoder;
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
    public void downloadFile(String id, String name, String path, boolean isPdf, boolean isPreview, HttpServletRequest request, HttpServletResponse response) throws Exception {
        // 抽取出来
        OutputStream out = null;
        FileInputStream in = null;
        try {
            File file = null;
            String appId = null;
            String tenantCode = null;
            if (Strings.isNotBlank(id)) {
                Attach attach = attachService.getModel(id);
                Assert.notNull(attach, ApiErrorMsg.IS_NULL);
                appId = attach.getAppId();
                tenantCode = attach.getTenantCode();
                file = downloadService.downloadFile(attach.getName(), attach.getPath());
                name = attach.getName();
            } else if (Strings.isNotBlank(path)) {
                file = downloadService.downloadFile(name, path);
                name = Strings.isNotBlank(name) ? name : file.getName();
            }
            if (file == null) {
                throw new Exception("File does not exist");
            }
            if (isPdf) {
                String ext = name.substring(name.lastIndexOf("."));
                name = Strings.isNotBlank(name) ? name.replace(ext, ".pdf") : null;
                String outputPath = UploadService.getSavePath(UploadService.ROOT_CONVERT_DIRECTORY, tenantCode, appId, "word-to-pdf.pdf", true);
                OfficeUtils.toPdf(file.getAbsolutePath(), outputPath, ext);
                File pFile = new File(outputPath);
                file = pFile.exists() ? pFile : null;
            }
            if (Strings.isNotBlank(name)) {
                out = response.getOutputStream();
                // 编码
                String encodeName = URLEncoder.encode(name, StandardCharsets.UTF_8);
                String mineType = request.getServletContext().getMimeType(encodeName);
                // 如果没有取到常用的媒体类型，则获取自配置的媒体类型
                if (mineType == null) {
                    mineType = EXT_MAP.get(UploadService.getFileExtensionWithNoDot(name));
                }
                response.setContentType(mineType);
                // 在线查看图片、pdf
                if (isPreview && Strings.isNotBlank(mineType) && (mineType.startsWith("image/") || mineType.equalsIgnoreCase(MediaTypes.APPLICATION_PDF))) {
                    //  file = downloadService.copyToFile(file, name);
                } else {
                    response.setHeader("Content-Disposition", "attachment; filename=" + encodeName);
                }
                // 读取文件
                in = new FileInputStream(file);
                int len = 0;
                byte[] buffer = new byte[1024];
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            throw e;
        } finally {
            if (out != null) {
                out.close();
            }
            if (in != null) {
                in.close();
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
            String ext = UploadService.getFileExtension(fileName);
            if (Strings.isBlank(ext) || !ext.equalsIgnoreCase(".config")) {
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
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        } finally {
            if (bufferedReader != null) {
                bufferedReader.close();
            }
        }
    }
}

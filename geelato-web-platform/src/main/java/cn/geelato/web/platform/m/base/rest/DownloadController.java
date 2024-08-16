package cn.geelato.web.platform.m.base.rest;


import cn.geelato.web.platform.m.base.entity.Attach;
import cn.geelato.web.platform.m.base.service.AttachService;
import cn.geelato.web.platform.m.base.service.DownloadService;
import cn.geelato.web.platform.m.base.service.UploadService;
import cn.geelato.web.platform.m.excel.entity.OfficeUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

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
@Controller
@RequestMapping(value = "/api/resources")
public class DownloadController extends BaseController {
    private final Logger logger = LoggerFactory.getLogger(DownloadController.class);
    @Autowired
    private DownloadService downloadService;
    @Autowired
    private AttachService attachService;

    // 设置不常用的媒体类型
    static final Map<String, String> EXT_MAP = new HashMap<>();
    static {
        // 前端基于esm文件打包的文件
        EXT_MAP.put("mjs", "application/javascript");
    }

    @RequestMapping(value = "/file", method = RequestMethod.GET)
    @ResponseBody
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
            if (isPdf) {
                String ext = name.substring(name.lastIndexOf("."));
                name = Strings.isNotBlank(name) ? name.replace(ext, ".pdf") : null;
                String outputPath = UploadService.getSavePath(UploadService.ROOT_CONVERT_DIRECTORY, tenantCode, appId, "word-to-pdf.pdf", true);
                OfficeUtils.toPdf(file.getAbsolutePath(), outputPath, ext);
                File pFile = new File(outputPath);
                file = pFile.exists() ? pFile : null;
            }
            if (file != null && Strings.isNotBlank(name)) {
                out = response.getOutputStream();
                // 编码
                String encodeName = URLEncoder.encode(name, StandardCharsets.UTF_8);
                String mineType = request.getServletContext().getMimeType(encodeName);
                // 如果没有取到常用的媒体类型，则获取自配置的媒体类型
                if(mineType==null){
                    mineType = EXT_MAP.get(UploadService.getFileExtensionWithNoDot(name));
                }
                response.setContentType(mineType);
                // 在线查看图片、pdf
                if (isPreview && Strings.isNotBlank(mineType) && (mineType.startsWith("image/") || mineType.equalsIgnoreCase("application/pdf"))) {
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
            } else {
                throw new Exception("文件不存在");
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
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
    @ResponseBody
    public ApiResult downloadJson(String fileName) throws IOException {
        ApiResult result = new ApiResult();
        if (Strings.isBlank(fileName)) {
            return result.success().setMsg("fileName is null");
        }
        BufferedReader bufferedReader = null;
        try {
            String ext = UploadService.getFileExtension(fileName);
            if (Strings.isBlank(ext) || !ext.equalsIgnoreCase(".config")) {
                fileName += ".config";
            }
            File file = new File(String.format("%s/%s", UploadService.ROOT_CONFIG_DIRECTORY, fileName));
            if (!file.exists()) {
                return result.success().setMsg("File (.config) does not exist");
            }
            StringBuilder contentBuilder = new StringBuilder();
            bufferedReader = Files.newBufferedReader(Paths.get(file.getAbsolutePath()), StandardCharsets.UTF_8);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                contentBuilder.append(line);
            }
            result.setData(contentBuilder.toString());
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.success().setMsg(e.getMessage());
        } finally {
            if (bufferedReader != null) {
                bufferedReader.close();
            }
        }

        return result;
    }
}

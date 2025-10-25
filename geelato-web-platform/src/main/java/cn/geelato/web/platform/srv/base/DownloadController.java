package cn.geelato.web.platform.srv.base;


import cn.geelato.web.common.constants.MediaTypes;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.utils.FileUtils;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.handler.file.FileHandler;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.base.service.UploadService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final FileHandler fileHandler;

    @Autowired
    public DownloadController(FileHandler fileHandler) {
        this.fileHandler = fileHandler;
    }

    @RequestMapping(value = "/file", method = RequestMethod.GET)
    public void downloadFile(String id, String name, String path, boolean isPdf, boolean isPreview, boolean isThumbnail) throws Exception {
        fileHandler.download(id, isPdf, isPreview, isThumbnail, this.request, this.response);
    }

    @RequestMapping(value = "/json", method = RequestMethod.GET)
    public ApiResult<?> downloadJson(String fileName) throws IOException {
        if (Strings.isBlank(fileName)) {
            return ApiResult.fail("fileName is null");
        }
        BufferedReader bufferedReader = null;
        try {
            String ext = FileUtils.getFileExtension(fileName);
            if (Strings.isBlank(ext) || !UploadService.ROOT_CONFIG_SUFFIX.equalsIgnoreCase(ext)) {
                fileName += UploadService.ROOT_CONFIG_SUFFIX;
            }
            File file = new File(String.format("%s/%s", UploadService.getRootConfigDirectory(), fileName));
            if (!file.exists()) {
                return ApiResult.fail("该站点信息还未配置");
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

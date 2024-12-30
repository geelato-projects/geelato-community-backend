package cn.geelato.web.platform.m.base.service;

import cn.geelato.core.constants.MediaTypes;
import cn.geelato.utils.FileUtils;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.enums.AttachmentSourceEnum;
import cn.geelato.web.platform.handler.file.FileHandler;
import cn.geelato.web.platform.m.base.entity.Attachment;
import cn.geelato.web.platform.m.excel.entity.OfficeUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * @author diabl
 */
@Component
public class DownloadService {
    // 设置不常用的媒体类型
    static final Map<String, String> EXT_MAP = new HashMap<>();

    static {
        // 前端基于esm文件打包的文件
        EXT_MAP.put("mjs", MediaTypes.APPLICATION_JAVASCRIPT);
    }

    /**
     * 下载文件。
     *
     * @param file     需要下载的文件对象
     * @param name     下载文件的名称
     * @param request  HttpServletRequest对象，用于获取请求信息
     * @param response HttpServletResponse对象，用于设置响应信息
     * @throws Exception 如果在下载文件过程中发生异常，则抛出该异常
     */
    public void downloadFile(File file, String name, boolean isPreview, HttpServletRequest request, HttpServletResponse response, String mineType) throws IOException {
        OutputStream out = null;
        FileInputStream in = null;
        try {
            if (file == null || !file.exists() || StringUtils.isBlank(name)) {
                throw new RuntimeException("downloadFile: File does not exist");
            }
            out = response.getOutputStream();
            this.setResponse(request, response, name, isPreview, mineType);
            // 读取文件
            in = new FileInputStream(file);
            int len = 0;
            byte[] buffer = new byte[1024];
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            out.flush();
        } catch (Exception e) {
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

    public void downloadFile(InputStream in, String name, boolean isPreview, HttpServletRequest request, HttpServletResponse response, String mineType) throws IOException {
        OutputStream out = null;
        try {
            if (in == null || StringUtils.isBlank(name)) {
                throw new RuntimeException("downloadFile: File does not exist");
            }
            out = response.getOutputStream();
            this.setResponse(request, response, name, isPreview, mineType);
            // 读取文件
            int len = 0;
            byte[] buffer = new byte[1024];
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            out.flush();
        } catch (Exception e) {
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

    /**
     * 下载文件
     *
     * @param attachment 附件信息对象
     * @param request    HttpServletRequest对象
     * @param response   HttpServletResponse对象
     * @throws Exception 如果在下载文件过程中出现异常，则抛出该异常
     */
    public void downloadFile(Attachment attachment, boolean isPreview, HttpServletRequest request, HttpServletResponse response, String mineType) throws Exception {
        this.downloadFile(new File(attachment.getPath()), attachment.getName(), isPreview, request, response, mineType);
    }

    /**
     * 设置响应头和媒体类型
     *
     * @param request   HttpServletRequest对象，用于获取ServletContext
     * @param response  HttpServletResponse对象，用于设置响应头
     * @param name      文件名，用于获取媒体类型
     * @param isPreview 是否为预览模式
     */
    public void setResponse(HttpServletRequest request, HttpServletResponse response, String name, boolean isPreview, String mineType) {
        // 编码
        String encodeName = URLEncoder.encode(name, StandardCharsets.UTF_8);
        if (mineType == null) {
            mineType = request.getServletContext().getMimeType(encodeName);
            // 如果没有取到常用的媒体类型，则获取自配置的媒体类型
            if (mineType == null) {
                mineType = EXT_MAP.get(FileUtils.getFileExtensionWithNoDot(name));
            }
        }
        response.setContentType(mineType);
        // 在线查看图片、pdf
        if (isPreview && Strings.isNotBlank(mineType) && (mineType.startsWith("image/") || mineType.equalsIgnoreCase(MediaTypes.APPLICATION_PDF))) {
            //  file = downloadService.copyToFile(file, name);
        } else {
            response.setHeader("Content-Disposition", "attachment; filename=" + encodeName);
        }
    }

}

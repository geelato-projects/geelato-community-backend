package cn.geelato.web.platform.handler.file;

import cn.geelato.utils.FileUtils;
import cn.geelato.web.platform.m.base.service.DownloadService;
import cn.geelato.web.platform.m.base.service.UploadService;
import cn.geelato.web.platform.m.excel.entity.OfficeUtils;
import cn.geelato.web.platform.m.file.entity.Attachment;
import cn.geelato.web.platform.m.file.handler.AccessoryHandler;
import cn.geelato.web.platform.m.file.param.FileParam;
import cn.geelato.web.platform.m.file.utils.FileParamUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class BaseHandler {
    protected final AccessoryHandler accessoryHandler;
    private final DownloadService downloadService;

    @Autowired
    public BaseHandler(AccessoryHandler accessoryHandler, DownloadService downloadService) {
        this.accessoryHandler = accessoryHandler;
        this.downloadService = downloadService;
    }

    /**
     * 根据附件ID和是否为缩略图获取附件
     *
     * @param id          附件ID
     * @param isThumbnail 是否为缩略图
     * @return 返回对应的附件对象
     */
    public Attachment getAttachment(String id, boolean isThumbnail) {
        return accessoryHandler.getAttachment(id, isThumbnail);
    }

    public Attachment getAttachment(String id) {
        return accessoryHandler.getAttachment(id, false);
    }

    /**
     * 获取附件列表
     *
     * @param params 查询参数
     * @return 返回附件列表
     */
    public List<Attachment> getAttachments(Map<String, Object> params) {
        return accessoryHandler.getAttachments(params);
    }

    public long countAttachments(Map<String, Object> params) {
        return accessoryHandler.countAttachments(params);
    }

    public Attachment updateAttachment(Attachment attachment) {
        return accessoryHandler.updateAttachment(attachment);
    }

    public void updateId(String tableType, String sourceId, String targetId, boolean isDelete) {
        accessoryHandler.updateId(tableType, sourceId, targetId, isDelete);
    }

    /**
     * 删除附件
     *
     * @param id        附件的ID
     * @param isRemoved 是否真正从数据库中删除附件，true表示删除，false表示标记为已删除
     */
    public void delete(String id, Boolean isRemoved) {
        accessoryHandler.delete(id, isRemoved);
    }

    /**
     * 保存附件信息
     *
     * @param file 要保存的文件
     * @param path 文件路径
     * @return 返回保存的附件对象
     * @throws IOException 如果发生I/O异常则抛出此异常
     */
    public Attachment save(File file, String path, FileParam param) throws IOException {
        return accessoryHandler.save(file, path, param);
    }

    /**
     * 保存附件信息, 指定文件名
     *
     * @param file 要保存的文件
     * @param name 文件名
     * @param path 文件路径
     * @return 保存的附件对象
     * @throws IOException 如果发生I/O异常则抛出此异常
     */
    public Attachment save(File file, String name, String path, FileParam param) throws IOException {
        return accessoryHandler.save(file, name, path, param);
    }

    /**
     * 上传附件到本地存储
     * <br/>指定存储位置；指定是否重命名；选择是否为缩略图；指定缩略图的尺寸
     *
     * @param file 上传的文件
     * @return 上传后的附件对象
     * @throws IOException 如果在上传过程中发生I/O错误，则抛出IOException
     */
    public Attachment uploadLocal(MultipartFile file, String path, FileParam param) throws IOException {
        return accessoryHandler.upload(file, path, param);
    }

    /**
     * 上传base64字符串文件到本地存储
     * <br/>指定存储位置；指定是否重命名；选择是否为缩略图；指定缩略图的尺寸
     *
     * @param base64String base64字符串，包含要上传的文件内容
     * @param name         文件名
     * @return 上传后的附件对象
     * @throws IOException 如果在上传过程中发生I/O错误，则抛出IOException
     */
    public Attachment uploadLocal(String base64String, String name, String path, FileParam param) throws IOException {
        return accessoryHandler.upload(base64String, name, path, param);
    }

    /**
     * 选择是否将文件转为pdf文件后下载
     * <br/>指定文件名称；选择是否预览
     *
     * @param inputStream 输入流，包含要下载的文件内容
     * @param name        下载文件的名称
     * @param isPdf       是否将文件转换为PDF格式进行预览
     * @param isPreview   是否为预览模式
     * @param request     HttpServletRequest对象
     * @param response    HttpServletResponse对象
     * @param uuid        文件的唯一标识符
     * @param appId       应用ID
     * @param tenantCode  租户代码
     * @throws IOException 如果在下载或文件操作过程中发生I/O错误，则抛出IOException
     */
    public void download(InputStream inputStream, String name, boolean isPdf, boolean isPreview, HttpServletRequest request, HttpServletResponse response, String uuid, String appId, String tenantCode) throws IOException {
        if (isPdf) {
            File tempFile = null;
            try {
                String fileExt = FileUtils.getFileExtension(name);
                tempFile = FileUtils.createTempFile(inputStream, fileExt);
                File file = toPdf(tempFile, fileExt, appId, tenantCode);
                String fileName = FileUtils.setPdfFileName(name);
                download(file, fileName, isPreview, request, response, uuid);
            } finally {
                if (tempFile != null) {
                    tempFile.delete();
                }
            }
        } else {
            download(inputStream, name, isPreview, request, response, uuid);
        }
    }

    /**
     * 选择是否将文件转为pdf文件后下载
     * <br/>指定文件名称；选择是否预览
     *
     * @param path       文件的路径
     * @param name       下载文件的名称
     * @param isPdf      是否将文件转换为PDF格式
     * @param isPreview  是否为预览模式
     * @param request    HttpServletRequest对象
     * @param response   HttpServletResponse对象
     * @param uuid       文件的唯一标识符
     * @param appId      应用ID
     * @param tenantCode 租户代码
     * @throws IOException 如果在下载过程中发生I/O错误，则抛出IOException
     */
    public void download(String path, String name, boolean isPdf, boolean isPreview, HttpServletRequest request, HttpServletResponse response, String uuid, String appId, String tenantCode) throws IOException {
        // 加载文件
        File file = FileUtils.pathToFile(path);
        // 转换PDF文件
        if (isPdf) {
            String fileExt = FileUtils.getFileExtension(name);
            file = toPdf(file, fileExt, appId, tenantCode);
            name = FileUtils.setPdfFileName(name);
        }
        // 下载文件
        download(file, name, isPreview, request, response, uuid);
    }

    /**
     * 下载文件
     * <br/>指定文件名称；选择是否预览
     *
     * @param file      待下载的文件对象
     * @param name      下载文件的名称
     * @param isPreview 是否为预览模式
     * @param request   HttpServletRequest对象
     * @param response  HttpServletResponse对象
     * @param uuid      文件的唯一标识符
     * @throws IOException 如果在下载过程中发生I/O错误，则抛出IOException
     */
    public void download(File file, String name, boolean isPreview, HttpServletRequest request, HttpServletResponse response, String uuid) throws IOException {
        FileUtils.validateExist(file);
        // 设置缓存
        response.setHeader("Cache-Control", "public, max-age=3600, must-revalidate");
        response.setHeader("ETag", uuid);
        response.setDateHeader("Last-Modified", file.lastModified());
        // 下载
        downloadService.downloadFile(file, name, isPreview, request, response, null);
    }

    /**
     * 下载输入流
     * <br/>指定文件名称；选择是否预览
     *
     * @param inputStream 输入流，包含要下载的文件内容
     * @param name        下载文件的名称
     * @param isPreview   是否为预览模式
     * @param request     HttpServletRequest对象
     * @param response    HttpServletResponse对象
     * @param uuid        文件的唯一标识符
     * @throws IOException 如果在下载过程中发生I/O错误，则抛出IOException
     */
    public void download(InputStream inputStream, String name, boolean isPreview, HttpServletRequest request, HttpServletResponse response, String uuid) throws IOException {
        // 设置缓存
        response.setHeader("Cache-Control", "public, max-age=3600, must-revalidate");
        response.setHeader("ETag", uuid);
        // 下载
        downloadService.downloadFile(inputStream, name, isPreview, request, response, null);
    }


    /**
     * 将指定文件转换为PDF格式并保存到指定路径。
     *
     * @param sourceFile 要转换的文件
     * @param sourceExt  文件原始扩展名
     * @param appId      应用ID
     * @param tenantCode 租户代码
     * @return 转换后的PDF文件，如果转换失败则返回null
     * @throws Exception 如果转换过程中发生异常，则抛出该异常
     */
    public File toPdf(File sourceFile, String sourceExt, String appId, String tenantCode) {
        if (sourceFile != null && sourceFile.exists() && Strings.isNotBlank(sourceExt)) {
            try {
                // 指定地址
                String outputPath = UploadService.getSavePath(UploadService.ROOT_CONVERT_DIRECTORY, tenantCode, appId, "word-to-pdf.pdf", true);
                // 转为pdf文件
                OfficeUtils.toPdf(sourceFile.getAbsolutePath(), outputPath, sourceExt);
                // 返回文件
                return FileUtils.pathToFile(outputPath);
            } catch (Exception e) {
                log.error("toPdf: error", e);
            }
        }
        return null;
    }


    /**
     * 将指定文件转换为PDF格式并保存。
     *
     * @param tableType  表类型
     * @param sourceFile 源文件
     * @param sourceName 源文件名
     * @param appId      应用ID
     * @param tenantCode 租户代码
     * @return 转换后的PDF附件对象
     * @throws Exception 如果在转换或保存过程中发生异常
     */
    public Attachment toPdf(String tableType, File sourceFile, String sourceName, String appId, String tenantCode) throws Exception {
        String ext = FileUtils.getFileExtension(sourceName);
        String name = FileUtils.setPdfFileName(sourceName);
        // 转为pdf
        File file = this.toPdf(sourceFile, ext, appId, tenantCode);
        if (file == null) {
            throw new RuntimeException("toPdfAndSave: PDF File does not exist");
        }
        FileParam fileParam = FileParamUtils.byLocal(tableType, "TOPDF", appId, tenantCode);
        return accessoryHandler.save(file, name, file.getPath(), fileParam);
    }

    public Attachment toPdf(String tableType, Attachment attachment) throws Exception {
        return toPdf(tableType, FileUtils.pathToFile(attachment.getPath()), attachment.getName(), attachment.getAppId(), attachment.getTenantCode());
    }
}

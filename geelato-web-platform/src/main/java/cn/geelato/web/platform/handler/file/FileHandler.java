package cn.geelato.web.platform.handler.file;

import cn.geelato.utils.FileUtils;
import cn.geelato.utils.ImageUtils;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.oss.OSSResult;
import cn.geelato.web.platform.common.Base64Helper;
import cn.geelato.web.platform.common.FileHelper;
import cn.geelato.web.platform.enums.AttachmentServiceEnum;
import cn.geelato.web.platform.handler.attachment.AccessoryHandler;
import cn.geelato.web.platform.m.base.entity.Attachment;
import cn.geelato.web.platform.m.base.service.DownloadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

@Component
public class FileHandler extends BaseHandler {
    private final FileHelper fileHelper;

    public FileHandler(AccessoryHandler accessoryHandler, DownloadService downloadService, FileHelper fileHelper) {
        super(accessoryHandler, downloadService);
        this.fileHelper = fileHelper;
    }

    /**
     * 上传文件（多服务器）
     * <br/>设置：信息存放位置；生产缩略图
     * <br/>本地服务器可设置文件路径
     *
     * @param file        上传的文件
     * @param serviceType 服务类型，决定上传方式
     * @param tableType   表类型
     * @param genre       文件类型
     * @param root        根目录路径
     * @param isThumbnail 是否为缩略图
     * @param isRename    是否重命名文件
     * @param dimension   文件维度
     * @param appId       应用ID
     * @param tenantCode  租户代码
     * @return 上传后的附件对象
     * @throws IOException 如果在上传过程中发生I/O错误，则抛出IOException
     */
    public Attachment upload(MultipartFile file, String serviceType, String tableType, String genre, String root, boolean isThumbnail, Boolean isRename, Integer dimension, String appId, String tenantCode) throws IOException {
        if (AttachmentServiceEnum.OSS_ALI.getValue().equalsIgnoreCase(serviceType)) {
            return upload(tableType, file, genre, isThumbnail, dimension, appId, tenantCode);
        } else {
            return upload(tableType, file, genre, root, isRename, isThumbnail, dimension, appId, tenantCode);
        }
    }

    public Attachment upload(String base64String, String name, String serviceType, String tableType, String genre, boolean isThumbnail, Boolean isRename, Integer dimension, String appId, String tenantCode) throws IOException {
        if (AttachmentServiceEnum.OSS_ALI.getValue().equalsIgnoreCase(serviceType)) {
            return upload(tableType, base64String, name, genre, isThumbnail, dimension, appId, tenantCode);
        } else {
            return upload(tableType, base64String, name, genre, isRename, isThumbnail, dimension, appId, tenantCode);
        }
    }

    /**
     * 上传文件（阿里云存储）
     * <br/>设置：信息存放位置；生产缩略图
     *
     * @param tableType   表类型
     * @param file        上传的文件
     * @param genre       文件类型
     * @param isThumbnail 是否为缩略图
     * @param dimension   缩略图尺寸
     * @param appId       应用ID
     * @param tenantCode  租户代码
     * @return 上传后的附件对象
     * @throws IOException 如果在上传过程中发生I/O错误，则抛出IOException
     */
    public Attachment upload(String tableType, MultipartFile file, String genre, boolean isThumbnail, Integer dimension, String appId, String tenantCode) throws IOException {
        Attachment attachment = null;
        OSSResult ossResult = fileHelper.putFile(file);
        if (ossResult.getSuccess() == null || ossResult.getSuccess()) {
            if (ossResult.getOssFile() != null) {
                attachment = accessoryHandler.save(tableType, file, ossResult.getOssFile().getObjectName(), ossResult.getOssFile().getObjectId(), genre, appId, tenantCode);
            }
        }
        if (attachment != null && isThumbnail) {
            // 生成缩略图
            File thumbnail = createThumbnail(file, dimension);
            // 上传缩略图，并保存缩略图信息，并更新附件ID
            uploadThumbnail(tableType, thumbnail, attachment.getName(), attachment.getGenre(), attachment.getId(), attachment.getAppId(), attachment.getTenantCode());
        }
        return attachment;
    }


    /**
     * 读取本地文件上传（阿里云存储）
     * <br/>设置：信息存放位置；生产缩略图
     *
     * @param tableType   表类型
     * @param file        待上传的文件对象
     * @param name        文件名
     * @param genre       文件类型
     * @param isThumbnail 是否为缩略图
     * @param dimension   缩略图尺寸
     * @param appId       应用ID
     * @param tenantCode  租户代码
     * @return 上传后的附件对象
     * @throws IOException 如果在上传过程中发生I/O错误，则抛出IOException
     */
    public Attachment upload(String tableType, File file, String name, String genre, boolean isThumbnail, Integer dimension, String appId, String tenantCode) throws IOException {
        Attachment attachment = null;
        OSSResult ossResult = fileHelper.putFile(name, FileUtils.openInputStream(file));
        if (ossResult.getSuccess() == null || ossResult.getSuccess()) {
            if (ossResult.getOssFile() != null) {
                attachment = accessoryHandler.save(tableType, file, name, ossResult.getOssFile().getObjectName(), ossResult.getOssFile().getObjectId(), genre, appId, tenantCode);
            }
        }
        if (attachment != null && isThumbnail) {
            // 生成缩略图
            File thumbnail = createThumbnail(file, dimension);
            // 上传缩略图，并保存缩略图信息，并更新附件ID
            uploadThumbnail(tableType, thumbnail, name, attachment.getGenre(), attachment.getId(), attachment.getAppId(), attachment.getTenantCode());
        }
        return attachment;
    }

    /**
     * base64字符串文件上传（阿里云存储）
     * <br/>设置：信息存放位置；生产缩略图
     *
     * @param tableType    表类型
     * @param base64String Base64编码的字符串，表示要上传的文件内容
     * @param name         文件名称
     * @param genre        文件类型
     * @param isThumbnail  是否作为缩略图上传
     * @param dimension    文件尺寸（仅当isThumbnail为true时有效）
     * @param appId        应用ID
     * @param tenantCode   租户代码
     * @return 上传后的附件对象
     * @throws IOException 如果在文件上传过程中发生I/O错误
     */
    public Attachment upload(String tableType, String base64String, String name, String genre, boolean isThumbnail, Integer dimension, String appId, String tenantCode) throws IOException {
        File tempFile = null;
        try {
            tempFile = FileUtils.createTempFile(base64String, name);
            return upload(tableType, tempFile, name, genre, isThumbnail, dimension, appId, tenantCode);
        } finally {
            if (tempFile != null) {
                tempFile.delete();
            }
        }
    }

    /**
     * 下载附件文件（多服务器）
     * <br/>是否转为pdf文件；是否预览模式；是否下载缩略图
     *
     * @param id          附件的唯一标识符
     * @param isPdf       是否将文件转换为PDF格式进行预览
     * @param isPreview   是否为预览模式
     * @param isThumbnail 是否下载缩略图
     * @param request     HttpServletRequest对象
     * @param response    HttpServletResponse对象
     * @throws IOException 如果在下载或文件操作过程中发生I/O错误，则抛出IOException
     */
    public void download(String id, boolean isPdf, boolean isPreview, boolean isThumbnail, HttpServletRequest request, HttpServletResponse response) throws IOException {
        Attachment attachment = accessoryHandler.getAttachment(id, isThumbnail);
        Assert.notNull(attachment, "file not found");
        if (Strings.isNotBlank(attachment.getObjectId())) {
            OSSResult ossResult = fileHelper.getFile(attachment.getPath());
            if (!ossResult.getSuccess() || ossResult.getOssFile() == null || ossResult.getOssFile().getFileMeta() == null) {
                throw new FileNotFoundException("The OSS file does not exist or cannot be accessed");
            }
            try (InputStream inputStream = ossResult.getOssFile().getFileMeta().getFileInputStream()) {
                download(inputStream, attachment.getName(), isPdf, isPreview, request, response, attachment.getId(), attachment.getAppId(), attachment.getTenantCode());
            }
        } else {
            download(attachment.getPath(), attachment.getName(), isPdf, isPreview, request, response, attachment.getId(), attachment.getAppId(), attachment.getTenantCode());
        }
    }

    /**
     * 创建缩略图（临时文件）
     *
     * @param file      要生成缩略图的多部分文件
     * @param dimension 缩略图的尺寸，如果为null则不进行尺寸限制
     * @return 生成的缩略图文件，如果无法生成则返回null
     * @throws IOException 如果文件操作过程中出现I/O异常
     */
    public File createThumbnail(MultipartFile file, Integer dimension) throws IOException {
        // 原始临时文件
        String fileExt = FileUtils.getFileExtension(file.getOriginalFilename());
        File sourceFile = FileUtils.createTempFile(file.getInputStream(), fileExt);
        // 缩略图临时文件
        try {
            int dis = dimension == null ? 0 : dimension.intValue();
            if (ImageUtils.isThumbnail(sourceFile, dis)) {
                File targetFile = FileUtils.createTempFile(fileExt);
                ImageUtils.thumbnail(sourceFile, targetFile, dis);
                if (!targetFile.exists()) {
                    throw new RuntimeException("thumbnail save failed");
                }
                return targetFile;
            }
        } finally {
            if (sourceFile != null) {
                sourceFile.delete();
            }
        }
        return null;
    }

    /**
     * 创建缩略图（临时文件）
     *
     * @param sourceFile 原始文件
     * @param dimension  缩略图的尺寸，如果为null则不进行尺寸限制
     * @return 生成的缩略图文件，如果无法生成则返回null
     * @throws IOException 如果文件操作过程中出现I/O异常
     */
    public File createThumbnail(File sourceFile, Integer dimension) throws IOException {
        // 原始临时文件
        String fileExt = FileUtils.getFileExtension(sourceFile.getName());
        // 缩略图临时文件
        int dis = dimension == null ? 0 : dimension.intValue();
        if (ImageUtils.isThumbnail(sourceFile, dis)) {
            File targetFile = FileUtils.createTempFile(fileExt);
            ImageUtils.thumbnail(sourceFile, targetFile, dis);
            if (!targetFile.exists()) {
                throw new RuntimeException("thumbnail save failed");
            }
            return targetFile;
        }
        return null;
    }

    /**
     * 上传临时缩略图（阿里云服务器）
     *
     * @param tableType  表类型
     * @param thumbnail  缩略图文件
     * @param name       缩略图名称
     * @param genre      文件类型
     * @param sourceId   源文件ID
     * @param appId      应用ID
     * @param tenantCode 租户代码
     * @throws IOException 如果文件上传过程中出现I/O异常
     */
    public void uploadThumbnail(String tableType, File thumbnail, String name, String genre, String sourceId, String appId, String tenantCode) throws IOException {
        if (thumbnail != null) {
            try {
                // 上传缩略图
                String thumbnailGenre = StringUtils.splice(",", genre, ImageUtils.THUMBNAIL_GENRE);
                Attachment target = upload(tableType, thumbnail, name, thumbnailGenre, false, null, appId, tenantCode);
                // 更新附件ID
                accessoryHandler.updateThumbnailId(tableType, sourceId, target.getId());
            } finally {
                if (thumbnail != null) {
                    thumbnail.delete();
                }
            }
        }
    }


    /**
     * 将附件转换为Base64格式的工具类对象
     *
     * @param id 附件的唯一标识符
     * @return 转换后的Base64Helper对象
     */
    public Base64Helper toBase64Helper(String id) {
        Attachment attachment = accessoryHandler.getAttachment(id, false);
        Base64Helper helper = new Base64Helper();
        if (attachment != null) {
            helper.setId(attachment.getId());
            helper.setName(attachment.getName());
            helper.setType(attachment.getType());
            helper.setSize(attachment.getSize());
            helper.setFile(toFile(attachment));
        }
        return helper;
    }

    /**
     * 根据附件的唯一标识符将附件转换为文件对象
     *
     * @param id 附件的唯一标识符
     * @return 转换后的文件对象
     */
    public File toFile(String id) {
        Attachment attachment = accessoryHandler.getAttachment(id, false);
        return toFile(attachment);
    }

    /**
     * 将附件对象转换为文件对象
     *
     * @param attachment 附件对象
     * @return 转换后的文件对象
     * @throws RuntimeException 如果文件下载失败，将抛出此异常
     */
    public File toFile(Attachment attachment) {
        if (attachment != null && Strings.isNotBlank(attachment.getObjectId())) {
            OSSResult ossResult = fileHelper.getFile(attachment.getPath());
            if (ossResult.getSuccess() && ossResult.getOssFile() != null && ossResult.getOssFile().getFileMeta() != null) {
                try (InputStream inputStream = ossResult.getOssFile().getFileMeta().getFileInputStream()) {
                    String fileExt = FileUtils.getFileExtension(attachment.getName());
                    return FileUtils.createTempFile(inputStream, fileExt);
                } catch (IOException e) {
                    throw new RuntimeException("file download failed");
                }
            }
        } else {
            return FileUtils.pathToFile(attachment.getPath());
        }
        return null;
    }
}

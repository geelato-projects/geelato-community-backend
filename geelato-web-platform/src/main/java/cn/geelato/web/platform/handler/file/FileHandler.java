package cn.geelato.web.platform.handler.file;

import cn.geelato.utils.FileUtils;
import cn.geelato.utils.ImageUtils;
import cn.geelato.utils.StringUtils;
import cn.geelato.utils.ZipUtils;
import cn.geelato.utils.entity.FileIS;
import cn.geelato.web.oss.OSSResult;
import cn.geelato.web.platform.common.Base64Helper;
import cn.geelato.web.platform.common.FileHelper;
import cn.geelato.web.platform.m.base.service.DownloadService;
import cn.geelato.web.platform.m.base.service.UploadService;
import cn.geelato.web.platform.m.file.entity.Attachment;
import cn.geelato.web.platform.m.file.enums.AttachmentServiceEnum;
import cn.geelato.web.platform.m.file.handler.AccessoryHandler;
import cn.geelato.web.platform.m.file.param.FileParam;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class FileHandler extends BaseHandler {
    private static final long COMPRESS_MAX_SIZE = 50 * 1024 * 1024;// 50M 压缩最大容量
    private static final int COMPRESS_MAX_AMOUNT = 1000; // 压缩最大数量
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
     * @param file 上传的文件
     * @return 上传后的附件对象
     * @throws IOException 如果在上传过程中发生I/O错误，则抛出IOException
     */
    public Attachment upload(MultipartFile file, String path, FileParam param) throws IOException {
        if (AttachmentServiceEnum.OSS_ALI.getValue().equalsIgnoreCase(param.getServiceType())) {
            return uploadCloud(file, param);
        } else {
            return uploadLocal(file, path, param);
        }
    }

    public Attachment upload(String base64String, String name, FileParam param) throws IOException {
        if (AttachmentServiceEnum.OSS_ALI.getValue().equalsIgnoreCase(param.getServiceType())) {
            return uploadCloud(base64String, name, param);
        } else {
            String path = UploadService.getSavePath(null, param.getServiceType(), param.getTenantCode(), param.getAppId(), name, false);
            return uploadLocal(base64String, name, path, param);
        }
    }

    /**
     * 上传文件（阿里云存储）
     * <br/>设置：信息存放位置；生产缩略图
     *
     * @param file 上传的文件
     * @return 上传后的附件对象
     * @throws IOException 如果在上传过程中发生I/O错误，则抛出IOException
     */
    public Attachment uploadCloud(MultipartFile file, FileParam param) throws IOException {
        Attachment attachment = null;
        OSSResult ossResult = fileHelper.putFile(file);
        if (ossResult.getSuccess() == null || ossResult.getSuccess()) {
            if (ossResult.getOssFile() != null) {
                param.setObjectId(ossResult.getOssFile().getObjectId());
                attachment = accessoryHandler.save(file, ossResult.getOssFile().getObjectName(), param);
            }
        }
        if (attachment != null && param.isThumbnail()) {
            // 生成缩略图
            File thumbnail = createThumbnail(file, param.getDimension(), param.getThumbScale());
            // 上传缩略图，并保存缩略图信息，并更新附件ID
            uploadThumbnail(thumbnail, attachment.getName(), attachment.getId(), new FileParam(param.getSourceType(), null, null, attachment.getGenre(), null, attachment.getAppId(), attachment.getTenantCode()));
        }
        return attachment;
    }

    /**
     * 读取本地文件上传（阿里云存储）
     * <br/>设置：信息存放位置；生产缩略图
     *
     * @param file 待上传的文件对象
     * @param name 文件名
     * @return 上传后的附件对象
     * @throws IOException 如果在上传过程中发生I/O错误，则抛出IOException
     */
    public Attachment uploadCloud(File file, String name, FileParam param) throws IOException {
        Attachment attachment = null;
        OSSResult ossResult = fileHelper.putFile(name, FileUtils.openInputStream(file));
        if (ossResult.getSuccess() == null || ossResult.getSuccess()) {
            if (ossResult.getOssFile() != null) {
                param.setObjectId(ossResult.getOssFile().getObjectId());
                attachment = accessoryHandler.save(file, name, ossResult.getOssFile().getObjectName(), param);
            }
        }
        if (attachment != null && param.isThumbnail()) {
            // 生成缩略图
            File thumbnail = createThumbnail(file, param.getDimension(), param.getThumbScale());
            // 上传缩略图，并保存缩略图信息，并更新附件ID
            uploadThumbnail(thumbnail, name, attachment.getId(), new FileParam(param.getSourceType(), null, null, attachment.getGenre(), null, attachment.getAppId(), attachment.getTenantCode()));
        }
        return attachment;
    }

    /**
     * base64字符串文件上传（阿里云存储）
     * <br/>设置：信息存放位置；生产缩略图
     *
     * @param base64String Base64编码的字符串，表示要上传的文件内容
     * @param name         文件名称
     * @return 上传后的附件对象
     * @throws IOException 如果在文件上传过程中发生I/O错误
     */
    public Attachment uploadCloud(String base64String, String name, FileParam param) throws IOException {
        File tempFile = null;
        try {
            tempFile = FileUtils.createTempFile(base64String, name);
            return uploadCloud(tempFile, name, param);
        } finally {
            if (tempFile != null) {
                tempFile.delete();
            }
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
    public File createThumbnail(MultipartFile file, Integer dimension, Double thumbScale) throws IOException {
        // 原始临时文件
        String fileExt = FileUtils.getFileExtension(file.getOriginalFilename());
        File sourceFile = FileUtils.createTempFile(file.getInputStream(), fileExt);
        // 缩略图临时文件
        try {
            int dis = dimension == null ? 0 : dimension.intValue();
            double ths = dimension == null ? 0 : thumbScale.doubleValue();
            if (ImageUtils.isThumbnail(sourceFile, dis)) {
                File targetFile = FileUtils.createTempFile(fileExt);
                ImageUtils.thumbnail(sourceFile, targetFile, dis, ths);
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
    public File createThumbnail(File sourceFile, Integer dimension, Double thumbScale) throws IOException {
        // 原始临时文件
        String fileExt = FileUtils.getFileExtension(sourceFile.getName());
        // 缩略图临时文件
        int dis = dimension == null ? 0 : dimension.intValue();
        double ths = dimension == null ? 0 : thumbScale.doubleValue();
        if (ImageUtils.isThumbnail(sourceFile, dis)) {
            File targetFile = FileUtils.createTempFile(fileExt);
            ImageUtils.thumbnail(sourceFile, targetFile, dis, ths);
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
     * @param thumbnail 缩略图文件
     * @param name      缩略图名称
     * @param sourceId  源文件ID
     * @throws IOException 如果文件上传过程中出现I/O异常
     */
    public void uploadThumbnail(File thumbnail, String name, String sourceId, FileParam param) throws IOException {
        if (thumbnail != null) {
            try {
                // 上传缩略图
                String thumbnailGenre = StringUtils.splice(",", param.getGenre(), ImageUtils.THUMBNAIL_GENRE);
                Attachment target = uploadCloud(thumbnail, name, new FileParam(param.getSourceType(), null, null, thumbnailGenre, null, param.getAppId(), param.getTenantCode()));
                // 更新附件ID
                accessoryHandler.updateThumbnailId(param.getSourceType(), sourceId, target.getId());
            } finally {
                if (thumbnail != null) {
                    thumbnail.delete();
                }
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

    public InputStream toInputStream(Attachment attachment) throws FileNotFoundException {
        if (attachment != null && Strings.isNotBlank(attachment.getObjectId())) {
            OSSResult ossResult = fileHelper.getFile(attachment.getPath());
            if (ossResult.getSuccess() && ossResult.getOssFile() != null && ossResult.getOssFile().getFileMeta() != null) {
                return ossResult.getOssFile().getFileMeta().getFileInputStream();
            }
        } else {
            File file = FileUtils.pathToFile(attachment.getPath());
            if (file != null) {
                return new FileInputStream(file);
            }
        }
        return null;
    }

    public List<Attachment> compress(String attachmentIds, String fileName, Integer maxNumber, FileParam param) throws IOException {
        List<Attachment> attachments = new ArrayList<>();
        // 参数验证
        if (Strings.isBlank(fileName)) {
            throw new IllegalArgumentException("File name is blank");
        }
        fileName = FileUtils.getFileName(fileName);
        List<String> ids = StringUtils.toListDr(attachmentIds);
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("Attachment id is empty");
        }
        // 获取附件信息
        List<Attachment> attachmentList = accessoryHandler.getAttachments(String.join(",", ids));
        if (attachmentList == null || attachmentList.isEmpty()) {
            throw new RuntimeException("Attachment not find");
        }
        // 获取附件文件
        Map<String, FileIS> fileMap = new HashMap<>();
        for (Attachment attachment : attachmentList) {
            InputStream inputStream = toInputStream(attachment);
            if (inputStream != null) {
                fileMap.put(attachment.getId(), new FileIS(attachment.getName(), inputStream));
            }
        }
        if (fileMap.isEmpty()) {
            throw new RuntimeException("Attachment file is not exist");
        }
        // 仅保留存在的文件
        attachmentList = attachmentList.stream().filter(attachment -> fileMap.containsKey(attachment.getId())).collect(Collectors.toList());
        // 分组
        int amount = maxNumber != null && maxNumber > 0 ? maxNumber.intValue() : COMPRESS_MAX_AMOUNT;
        List<List<Attachment>> lists = splitList(attachmentList, COMPRESS_MAX_SIZE, Math.min(COMPRESS_MAX_AMOUNT, amount));
        // 压缩
        for (int i = 0; i < lists.size(); i += 1) {
            List<String> fileIds = lists.get(i).stream().map(Attachment::getId).collect(Collectors.toList());
            String zipFileName = String.format("%s%s.zip", fileName, i > 0 ? "（" + i + "）" : "");
            String zpiFilePath = UploadService.getSavePath(UploadService.ROOT_DIRECTORY, param.getSourceType(), param.getTenantCode(), param.getAppId(), zipFileName, true);
            List<FileIS> fileISs = new ArrayList<>();
            for (Attachment attachment : lists.get(i)) {
                fileISs.add(fileMap.get(attachment.getId()));
            }
            ZipUtils.compressFiles(fileISs, zpiFilePath);
            // 上传至Oss，保存附件信息
            FileParam zipFileParam = new FileParam(param.getServiceType(), param.getSourceType(), null, String.join(",", fileIds), param.getGenre(), param.getInvalidTime(), param.getTenantCode(), param.getAppId());
            if (AttachmentServiceEnum.OSS_ALI.getValue().equalsIgnoreCase(param.getServiceType())) {
                attachments.add(uploadCloud(new File(zpiFilePath), zipFileName, zipFileParam));
            } else {
                attachments.add(save(new File(zpiFilePath), zipFileName, zpiFilePath, zipFileParam));
            }
        }
        return attachments;
    }

    private List<List<Attachment>> splitList(List<Attachment> list, long maxSize, int amount) {
        List<List<Attachment>> result = new ArrayList<>();
        List<Attachment> currentGroup = new ArrayList<>();
        long currentGroupSize = 0;

        for (Attachment item : list) {
            if (currentGroup.size() < amount && currentGroupSize + item.getSize() <= maxSize) {
                currentGroup.add(item);
                currentGroupSize += item.getSize();
            } else {
                if (!currentGroup.isEmpty()) {
                    result.add(new ArrayList<>(currentGroup));
                    currentGroup = new ArrayList<>();
                    currentGroupSize = 0;
                }
                if (currentGroup.size() < amount && currentGroupSize + item.getSize() <= maxSize) {
                    currentGroup.add(item);
                    currentGroupSize += item.getSize();
                } else {
                    List<Attachment> singleItemGroup = new ArrayList<>();
                    singleItemGroup.add(item);
                    result.add(singleItemGroup);
                }
            }
        }
        if (!currentGroup.isEmpty()) {
            result.add(new ArrayList<>(currentGroup));
        }

        return result;
    }
}

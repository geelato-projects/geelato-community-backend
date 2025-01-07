package cn.geelato.web.platform.handler.file;

import cn.geelato.utils.FileUtils;
import cn.geelato.utils.StringUtils;
import cn.geelato.utils.ThumbnailUtils;
import cn.geelato.utils.ZipUtils;
import cn.geelato.utils.entity.FileIS;
import cn.geelato.utils.entity.Resolution;
import cn.geelato.web.oss.OSSResult;
import cn.geelato.web.platform.common.Base64Helper;
import cn.geelato.web.platform.common.FileHelper;
import cn.geelato.web.platform.m.base.service.DownloadService;
import cn.geelato.web.platform.m.base.service.UploadService;
import cn.geelato.web.platform.m.file.entity.Attachment;
import cn.geelato.web.platform.m.file.enums.AttachmentServiceEnum;
import cn.geelato.web.platform.m.file.handler.AccessoryHandler;
import cn.geelato.web.platform.m.file.param.FileParam;
import cn.geelato.web.platform.m.file.param.ThumbnailResolution;
import cn.geelato.web.platform.m.file.utils.FileParamUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.*;
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

    public Attachment uploadCloud(File file, String name, FileParam param) throws IOException {
        OSSResult ossResult = fileHelper.putFile(name, FileUtils.openInputStream(file));
        if (ossResult.getSuccess() == null || ossResult.getSuccess()) {
            if (ossResult.getOssFile() != null) {
                param.setObjectId(ossResult.getOssFile().getObjectId());
                return accessoryHandler.save(file, name, ossResult.getOssFile().getObjectName(), param);
            }
        }
        return null;
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
        String fileExt = FileUtils.getFileExtension(file.getOriginalFilename());
        File tempFile = FileUtils.createTempFile(file.getInputStream(), fileExt);
        try {
            return uploadCloudAndThumb(tempFile, file.getOriginalFilename(), param);
        } finally {
            if (tempFile != null) {
                tempFile.delete();
            }
        }
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
            return uploadCloudAndThumb(tempFile, name, param);
        } finally {
            if (tempFile != null) {
                tempFile.delete();
            }
        }
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
    public Attachment uploadCloudAndThumb(File file, String name, FileParam param) throws IOException {
        if (param.isThumbnail() && ThumbnailUtils.isImage(file)) {
            Map<Integer, Attachment> targetMap = new HashMap<>();
            // 生成缩略图
            String fileExt = FileUtils.getFileExtension(name);
            List<ThumbnailResolution> thumbnails = createThumbnail(file, fileExt, param.getDimension(), param.getThumbScale());
            if (thumbnails != null && !thumbnails.isEmpty()) {
                // 上传并保存缩略图信息
                for (ThumbnailResolution tr : thumbnails) {
                    FileParam fileParam = param.toFileParam();
                    fileParam.setResolution(tr.getProduct());
                    fileParam.setGenre(StringUtils.splice(",", param.getGenre(), ThumbnailUtils.THUMBNAIL_GENRE));
                    Attachment target = uploadCloud(tr.getFile(), name, fileParam);
                    targetMap.put(tr.getAmass(), target);
                    tr.getFile().delete();
                }
            }
            // 如果只需要缩略图，则删除原始文件，否则保存原始文件
            if (!targetMap.isEmpty() && param.isOnlyThumb()) {
            } else {
                // 生成缩略图并保存到数据库
                Resolution resolution = Resolution.get(file);
                FileParam fileParam = param.toFileParam();
                fileParam.setResolution(resolution.getProduct());
                Attachment target = uploadCloud(file, name, fileParam);
                targetMap.put(resolution.getAmass(), target);
            }
            Attachment parent = getParentThumbnail(targetMap);
            if (parent != null) {
                List<String> ids = targetMap.values().stream().map(Attachment::getId).collect(Collectors.toList());
                accessoryHandler.updateChildThumbnail(param.getSourceType(), parent.getId(), ids);
            }
            return parent;
        } else {
            return uploadCloud(file, name, param);
        }
    }

    public Attachment getParentThumbnail(Map<Integer, Attachment> thumbMap) {
        if (!thumbMap.isEmpty()) {
            Optional<Integer> optional = thumbMap.keySet().stream().sorted(Comparator.reverseOrder()).findFirst();
            return thumbMap.get(optional.get());
        }
        return null;
    }

    public List<ThumbnailResolution> createThumbnail(File file, String fileExt, String dimension, String thumbScale) throws IOException {
        List<ThumbnailResolution> thumbnailResolutions = new ArrayList<>();
        // 根据原始图片的尺寸和缩略图比例，计算需要生成的缩略图的分辨率列表
        List<Resolution> thumbResolutions = ThumbnailUtils.resolution(file, dimension, thumbScale);
        if (thumbResolutions != null && !thumbResolutions.isEmpty()) {
            for (Resolution resolution : thumbResolutions) {
                File thumbFile = FileUtils.createTempFile(fileExt);
                ThumbnailUtils.thumbnail(file, thumbFile, resolution);
                if (thumbFile == null || !thumbFile.exists()) {
                    throw new RuntimeException("thumbnail save failed");
                }
                thumbnailResolutions.add(new ThumbnailResolution(resolution, thumbFile));
            }
        }
        return thumbnailResolutions;
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

    /**
     * 将附件转换为输入流
     *
     * @param attachment 附件对象
     * @return 附件对应的输入流，如果转换失败则返回null
     * @throws FileNotFoundException 如果附件文件不存在则抛出此异常
     */
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

    /**
     * 压缩附件列表
     *
     * @param attachmentIds 附件ID字符串，多个ID以逗号分隔
     * @param batchNos      批次号字符串，多个ID以逗号分隔
     * @param fileName      压缩文件的名称
     * @param maxNumber     每个压缩包中允许的最大附件数量，如果为null则使用默认数量
     * @param param         文件参数对象，包含服务类型、来源类型、文件类型、失效时间、租户代码、应用ID等信息
     * @return 压缩后的附件列表
     * @throws IOException 如果在文件操作中发生I/O异常
     */
    public List<Attachment> compress(String attachmentIds, String batchNos, String fileName, Integer maxNumber, FileParam param) throws IOException {
        List<Attachment> attachments = new ArrayList<>();
        // 参数验证
        if (Strings.isBlank(fileName)) {
            throw new IllegalArgumentException("File name is blank");
        }
        fileName = FileUtils.getFileName(fileName);
        // 查询参数
        Map<String, Object> queryParams = new HashMap<>();
        List<String> ids = StringUtils.toListDr(attachmentIds);
        if (ids != null && !ids.isEmpty()) {
            queryParams.put("ids", String.join(",", ids));
        }
        List<String> nos = StringUtils.toListDr(batchNos);
        if (nos != null && !nos.isEmpty()) {
            queryParams.put("batchNo", String.join(",", nos));
        }
        if (queryParams == null || queryParams.isEmpty()) {
            throw new IllegalArgumentException("Attachment ids and batch nos is blank");
        }
        // 获取附件信息
        List<Attachment> attachmentList = accessoryHandler.getAttachments(queryParams);
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
            FileParam zipFileParam = FileParamUtils.bySaveCompress(param.getServiceType(), param.getSourceType(), String.join(",", fileIds), param.getGenre(), param.getInvalidTime(), param.getBatchNo(), param.getTenantCode(), param.getAppId());
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

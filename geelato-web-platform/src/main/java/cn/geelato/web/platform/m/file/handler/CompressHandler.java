package cn.geelato.web.platform.m.file.handler;

import cn.geelato.utils.FileUtils;
import cn.geelato.web.platform.utils.ThumbnailUtils;
import cn.geelato.utils.entity.Resolution;
import cn.geelato.web.platform.m.file.entity.Compress;
import cn.geelato.web.platform.m.file.enums.AttachmentSourceEnum;
import cn.geelato.web.platform.m.file.param.AttachmentParam;
import cn.geelato.web.platform.m.file.param.ThumbnailParam;
import cn.geelato.web.platform.m.file.param.ThumbnailResolution;
import cn.geelato.web.platform.m.file.service.CompressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CompressHandler extends AttachmentHandler<Compress> {
    public static final String SQL_UPDATE_PID = "update platform_compress set pid = ? where id = ?";
    public static final String ATTACHMENT_SOURCE = AttachmentSourceEnum.PLATFORM_COMPRESS.getValue();
    private final CompressService compressService;

    @Autowired
    public CompressHandler(CompressService compressService) {
        this.compressService = compressService;
    }

    /**
     * 保存文件并返回附件对象
     *
     * @param file 要上传的文件
     * @param path 文件路径（地址或oss）
     * @return 保存后的附件对象
     */
    @Override
    public Compress save(MultipartFile file, String path, AttachmentParam param) {
        Compress model = build(file, path, param);
        return compressService.createModel(model);
    }

    @Override
    public Compress save(File file, String path, AttachmentParam param) throws IOException {
        Compress model = build(file, path, param);
        return compressService.createModel(model);
    }

    /**
     * 设置文件名，保存文件并返回附件对象
     */
    @Override
    public Compress save(File file, String name, String path, AttachmentParam param) throws IOException {
        Compress model = build(file, path, param);
        model.setName(name);
        return compressService.createModel(model);
    }

    /**
     * 根据提供的文件信息构建一个附件对象
     *
     * @param file 要上传的文件(name,type,size)
     * @param path 文件保存的路径
     * @return 构建好的附件对象
     */
    @Override
    public Compress build(MultipartFile file, String path, AttachmentParam param) {
        return build(new Compress(file), path, param);
    }

    @Override
    public Compress build(File file, String path, AttachmentParam param) throws IOException {
        return build(new Compress(file), path, param);
    }

    /**
     * 根据提供的文件信息构建一个附件对象，并设置文件名
     */
    @Override
    public Compress build(File file, String name, String path, AttachmentParam param) throws IOException {
        Compress model = new Compress(file);
        model.setName(name);
        return build(model, path, param);
    }

    /**
     * 更新附件信息
     */
    @Override
    public Compress update(Compress attachment) {
        return compressService.updateModel(attachment);
    }

    /**
     * 上传文件并保存到磁盘和数据库，同时可选生成缩略图
     *
     * @param file 要上传的文件
     * @param path 文件保存的路径
     * @return 上传后的附件对象
     * @throws IOException 如果在文件处理过程中发生I/O错误
     */
    @Override
    public Compress upload(MultipartFile file, String path, ThumbnailParam param) throws IOException {
        // 保存文件到磁盘
        FileUtils.saveFile(file.getBytes(), path);
        // 附件存附件表
        return thumbAndSave(FileUtils.pathToFile(path), file.getOriginalFilename(), path, param);
    }

    @Override
    public Compress upload(String base64String, String name, String path, ThumbnailParam param) throws IOException {
        // 保存文件到磁盘
        FileUtils.saveFile(base64String, path);
        // 附件存附件表
        return thumbAndSave(FileUtils.pathToFile(path), name, path, param);
    }

    @Override
    public Compress thumbAndSave(File file, String name, String path, ThumbnailParam param) throws IOException {
        if (param.isThumbnail() && ThumbnailUtils.isImage(file)) {
            Map<Integer, Compress> targetMap = new HashMap<>();
            // 生成缩略图
            List<ThumbnailResolution> thumbnails = createThumbnail(file, name, ATTACHMENT_SOURCE, param.getAppId(), param.getTenantCode(), param.getDimension(), param.getThumbScale());
            if (thumbnails != null && !thumbnails.isEmpty()) {
                // 保存到数据库
                for (ThumbnailResolution tr : thumbnails) {
                    AttachmentParam attachmentParam = param.toAttachmentParam();
                    attachmentParam.setResolution(tr.getProduct());
                    Compress target = save(tr.getFile(), name, tr.getPath(), attachmentParam);
                    targetMap.put(tr.getAmass(), target);
                }
            }
            // 如果只需要缩略图，则删除原始文件，否则保存原始文件
            if (!targetMap.isEmpty() && param.isOnlyThumb()) {
                // 则删除原始文件
                deleteFile(path);
            } else {
                // 生成缩略图并保存到数据库
                Resolution resolution = Resolution.get(file);
                AttachmentParam attachmentParam = param.toAttachmentParam();
                attachmentParam.setResolution(resolution.getProduct());
                targetMap.put(resolution.getAmass(), save(file, name, path, param.toAttachmentParam()));
            }
            Compress parent = getParentThumbnail(targetMap);
            if (parent != null) {
                List<String> ids = targetMap.values().stream().map(Compress::getId).collect(Collectors.toList());
                updateChildThumbnail(parent.getId(), ids);
            }
            return parent;
        } else {
            return save(file, name, path, param.toAttachmentParam());
        }
    }

    @Override
    public void updateChildThumbnail(String parentId, List<String> updateIds) {
        updateChildThumbnail(SQL_UPDATE_PID, parentId, updateIds);
    }

    /**
     * 逻辑删除文件
     *
     * @param attachment 要判断的附件对象
     */
    @Override
    public void isDelete(Compress attachment) {
        compressService.isDeleteModel(attachment);
    }

    @Override
    public void delete(Compress attachment) {
        compressService.deleteModel(Compress.class, attachment.getId());
        deleteFile(attachment.getPath());
    }
}

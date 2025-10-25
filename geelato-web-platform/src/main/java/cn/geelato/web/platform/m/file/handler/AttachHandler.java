package cn.geelato.web.platform.m.file.handler;

import cn.geelato.utils.FileUtils;
import cn.geelato.utils.StringUtils;
import cn.geelato.utils.entity.Resolution;
import cn.geelato.meta.Attach;
import cn.geelato.web.platform.m.file.enums.AttachmentSourceEnum;
import cn.geelato.web.platform.m.file.param.AttachmentParam;
import cn.geelato.web.platform.m.file.param.ThumbnailParam;
import cn.geelato.web.platform.m.file.param.ThumbnailResolution;
import cn.geelato.web.platform.m.file.service.AttachService;
import cn.geelato.web.platform.utils.ThumbnailUtils;
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
public class AttachHandler extends AttachmentHandler<Attach> {
    public static final String SQL_UPDATE_PID = "update platform_attach set pid = ? where id = ?";
    public static final String SQL_UPDATE_ID = "update platform_attach set id = ? where id = ?";
    public static final String SQL_UPDATE_ID_DEL = "update platform_attach set id = ?, del_status = 1, delete_at = now() where id = ?";
    public static final String ATTACHMENT_SOURCE = AttachmentSourceEnum.ATTACH.getValue();
    private final AttachService attachService;

    @Autowired
    public AttachHandler(AttachService attachService) {
        this.attachService = attachService;
    }


    /**
     * 保存文件并返回附件对象
     *
     * @param file 要上传的文件
     * @param path 文件路径（地址或oss）
     * @return 保存后的附件对象
     */
    @Override
    public Attach save(MultipartFile file, String path, AttachmentParam param) throws IOException {
        Attach model = build(file, path, param);
        return attachService.createModel(model);
    }

    @Override
    public Attach save(File file, String path, AttachmentParam param) throws IOException {
        Attach model = build(file, path, param);
        return attachService.createModel(model);
    }

    /**
     * 设置文件名，保存文件并返回附件对象
     */
    @Override
    public Attach save(File file, String name, String path, AttachmentParam param) throws IOException {
        Attach model = build(file, path, param);
        model.setName(name);
        return attachService.createModel(model);
    }

    /**
     * 根据提供的文件信息构建一个附件对象
     *
     * @param file 要上传的文件(name,type,size)
     * @param path 文件保存的路径
     * @return 构建好的附件对象
     */
    @Override
    public Attach build(MultipartFile file, String path, AttachmentParam param) throws IOException {
        Attach attach = new Attach();
        attach.setName(file.getOriginalFilename());
        attach.setType(file.getContentType());
        attach.setSize(file.getSize());
        return build(attach, path, param);
    }

    @Override
    public Attach build(File file, String path, AttachmentParam param) throws IOException {
        return build(new Attach(file), path, param);
    }

    /**
     * 根据提供的文件信息构建一个附件对象，并设置文件名
     */
    @Override
    public Attach build(File file, String name, String path, AttachmentParam param) throws IOException {
        Attach model = new Attach(file);
        model.setName(name);
        return build(model, path, param);
    }

    /**
     * 更新附件信息
     */
    @Override
    public Attach update(Attach attachment) {
        return attachService.updateModel(attachment);
    }

    /**
     * 创建附件信息
     *
     * @param attachment
     */
    @Override
    public Attach create(Attach attachment) {
        return attachService.createModel(attachment);
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
    public Attach upload(MultipartFile file, String path, ThumbnailParam param) throws IOException {
        // 保存文件到磁盘
        FileUtils.saveFile(file.getBytes(), path);
        // 附件存附件表
        return thumbAndSave(FileUtils.pathToFile(path), file.getOriginalFilename(), path, param);
    }

    @Override
    public Attach upload(String base64String, String name, String path, ThumbnailParam param) throws IOException {
        // 保存文件到磁盘
        FileUtils.saveFile(base64String, path);
        // 附件存附件表
        return thumbAndSave(FileUtils.pathToFile(path), name, path, param);
    }

    @Override
    public Attach upload(File file, String name, String path, ThumbnailParam param) throws IOException {
        // 保存文件到磁盘
        FileUtils.copyToFile(file, path);
        // 附件存附件表
        return thumbAndSave(FileUtils.pathToFile(path), name, path, param);
    }

    @Override
    public Attach thumbAndSave(File file, String name, String path, ThumbnailParam param) throws IOException {
        if (param.isThumbnail() && ThumbnailUtils.isImage(file)) {
            Map<Integer, Attach> targetMap = new HashMap<>();
            // 生成缩略图
            List<ThumbnailResolution> thumbnails = createThumbnail(file, name, ATTACHMENT_SOURCE, param.getAppId(), param.getTenantCode(), param.getDimension(), param.getThumbScale());
            if (thumbnails != null && !thumbnails.isEmpty()) {
                // 保存到数据库
                for (ThumbnailResolution tr : thumbnails) {
                    AttachmentParam attachmentParam = param.toAttachmentParam();
                    attachmentParam.setResolution(tr.getProduct());
                    attachmentParam.setGenre(StringUtils.splice(",", param.getGenre(), ThumbnailUtils.THUMBNAIL_GENRE));
                    Attach target = save(tr.getFile(), name, tr.getPath(), attachmentParam);
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
                targetMap.put(resolution.getAmass(), save(file, name, path, attachmentParam));
            }
            Attach parent = getParentThumbnail(targetMap);
            if (parent != null) {
                List<String> ids = targetMap.values().stream().map(Attach::getId).collect(Collectors.toList());
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

    @Override
    public void updateId(String sourceId, String targetId, boolean isDelete) {
        if (isDelete) {
            updateId(SQL_UPDATE_ID_DEL, sourceId, targetId);
        } else {
            updateId(SQL_UPDATE_ID, sourceId, targetId);
        }
    }

    /**
     * 逻辑删除文件
     *
     * @param attachment 要判断的附件对象
     */
    @Override
    public void isDelete(Attach attachment) {
        attachService.isDeleteModel(attachment);
    }

    @Override
    public void delete(Attach attachment) {
        attachService.deleteModel(Attach.class, attachment.getId());
        deleteFile(attachment.getPath());
    }
}

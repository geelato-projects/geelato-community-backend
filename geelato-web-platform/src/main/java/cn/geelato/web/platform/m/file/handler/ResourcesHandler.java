package cn.geelato.web.platform.m.file.handler;

import cn.geelato.utils.FileUtils;
import cn.geelato.web.platform.m.file.entity.Resources;
import cn.geelato.web.platform.m.file.enums.AttachmentSourceEnum;
import cn.geelato.web.platform.m.file.param.AttachmentParam;
import cn.geelato.web.platform.m.file.param.ThumbnailParam;
import cn.geelato.web.platform.m.file.service.ResourcesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@Component
public class ResourcesHandler extends AttachmentHandler<Resources> {
    public static final String SQL_UPDATE_ID = "update platform_resources set id = ? where id = ?";
    public static final String ATTACHMENT_SOURCE = AttachmentSourceEnum.PLATFORM_RESOURCES.getValue();
    private final ResourcesService resourcesService;

    @Autowired
    public ResourcesHandler(ResourcesService resourcesService) {
        this.resourcesService = resourcesService;
    }

    /**
     * 保存文件并返回附件对象
     *
     * @param file 要上传的文件
     * @param path 文件路径（地址或oss）
     * @return 保存后的附件对象
     */
    @Override
    public Resources save(MultipartFile file, String path, AttachmentParam param) {
        Resources model = build(file, path, param);
        return resourcesService.createModel(model);
    }

    @Override
    public Resources save(File file, String path, AttachmentParam param) throws IOException {
        Resources model = build(file, path, param);
        return resourcesService.createModel(model);
    }

    /**
     * 设置文件名，保存文件并返回附件对象
     */
    @Override
    public Resources save(File file, String name, String path, AttachmentParam param) throws IOException {
        Resources model = build(file, path, param);
        model.setName(name);
        return resourcesService.createModel(model);
    }

    /**
     * 根据提供的文件信息构建一个附件对象
     *
     * @param file 要上传的文件(name,type,size)
     * @param path 文件保存的路径
     * @return 构建好的附件对象
     */
    @Override
    public Resources build(MultipartFile file, String path, AttachmentParam param) {
        return build(new Resources(file), path, param);
    }

    @Override
    public Resources build(File file, String path, AttachmentParam param) throws IOException {
        return build(new Resources(file), path, param);
    }

    /**
     * 根据提供的文件信息构建一个附件对象，并设置文件名
     */
    @Override
    public Resources build(File file, String name, String path, AttachmentParam param) throws IOException {
        Resources model = new Resources(file);
        model.setName(name);
        return build(model, path, param);
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
    public Resources upload(MultipartFile file, String path, ThumbnailParam param) throws IOException {
        // 保存文件到磁盘
        FileUtils.saveFile(file.getBytes(), path);
        // 附件存附件表
        Resources model = save(file, path, param.toAttachmentParam());
        // 生成缩略图
        thumbnail(model, param);
        // 返回附件对象
        return model;
    }

    @Override
    public Resources upload(String base64String, String name, String path, ThumbnailParam param) throws IOException {
        // 保存文件到磁盘
        FileUtils.saveFile(base64String, path);
        // 附件存附件表
        Resources model = save(FileUtils.pathToFile(path), name, path, param.toAttachmentParam());
        // 生成缩略图
        thumbnail(model, param);
        // 返回附件对象
        return model;
    }

    /**
     * 为给定的附件生成缩略图
     *
     * @param source 要生成缩略图的原始附件对象
     * @return 如果生成了缩略图，则返回新的缩略图附件对象，否则返回null
     * @throws IOException 如果在生成缩略图或保存缩略图时发生I/O错误
     */
    @Override
    public Resources thumbnail(Resources source, ThumbnailParam param) throws IOException {
        if (param.isThumbnail()) {
            // 生成缩略图并构建缩略图附件对象
            Resources target = createThumbnail(source, ATTACHMENT_SOURCE, param.getDimension(), param.getThumbScale());
            if (target != null) {
                // 保存缩略图附件对象到数据库
                target = resourcesService.createModel(target);
                // 更新附件对象的缩略图ID
                target.setId(updateId(setThumbnailId(source.getId()), target.getId()));
                return target;
            }
        }
        return null;
    }

    /**
     * 更新指定目标ID为新的ID
     *
     * @param updateId 新的ID，用于替换目标ID
     * @param sourceId 目标ID，需要被更新的ID
     * @return 更新后的ID字符串
     */
    @Override
    public String updateId(String updateId, String sourceId) {
        dao.getJdbcTemplate().update(SQL_UPDATE_ID, updateId, sourceId);
        return updateId;
    }

    /**
     * 逻辑删除文件
     *
     * @param attachment 要判断的附件对象
     */
    @Override
    public void isDelete(Resources attachment) {
        resourcesService.isDeleteModel(attachment);
    }

    @Override
    public void delete(Resources attachment) {
        resourcesService.deleteModel(Resources.class, attachment.getId());
        deleteFile(attachment.getPath());
    }
}

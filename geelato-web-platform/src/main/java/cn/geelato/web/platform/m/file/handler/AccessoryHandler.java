package cn.geelato.web.platform.m.file.handler;

import cn.geelato.utils.SqlParams;
import cn.geelato.meta.Attach;
import cn.geelato.meta.Attachment;
import cn.geelato.meta.Compress;
import cn.geelato.meta.Resources;
import cn.geelato.web.platform.m.file.enums.FileGenreEnum;
import cn.geelato.web.platform.m.file.param.FileParam;
import com.alibaba.fastjson2.JSON;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 附件或资源处理器（本地存储）
 */
@Component
public class AccessoryHandler {
    private final AttachHandler attachHandler;
    private final CompressHandler compressHandler;
    private final ResourcesHandler resourcesHandler;

    public AccessoryHandler(AttachHandler attachHandler, CompressHandler compressHandler, ResourcesHandler resourcesHandler) {
        this.attachHandler = attachHandler;
        this.compressHandler = compressHandler;
        this.resourcesHandler = resourcesHandler;
    }

    /**
     * 上传到本地磁盘（MultipartFile）
     */
    public <T extends Attachment> T upload(MultipartFile file, String path, FileParam param) throws IOException {
        return (T) getAttachHandler(param.getSourceType()).upload(file, path, param.toThumbnailParam());
    }

    /**
     * 上传到本地磁盘（base64字符串文件）
     */
    public <T extends Attachment> T upload(String base64String, String name, String path, FileParam param) throws IOException {
        return (T) getAttachHandler(param.getSourceType()).upload(base64String, name, path, param.toThumbnailParam());
    }

    /**
     * 上传到本地磁盘（File）
     */
    public <T extends Attachment> T upload(File file, String name, String path, FileParam param) throws IOException {
        return (T) getAttachHandler(param.getSourceType()).upload(file, name, path, param.toThumbnailParam());
    }

    /**
     * 保存文件信息（MultipartFile）
     */
    public <T extends Attachment> T save(MultipartFile file, String path, FileParam param) throws IOException {
        return (T) getAttachHandler(param.getSourceType()).save(file, path, param.toAttachmentParam());
    }

    /**
     * 保存文件信息（File）
     */
    public <T extends Attachment> T save(File file, String path, FileParam param) throws IOException {
        return (T) getAttachHandler(param.getSourceType()).save(file, path, param.toAttachmentParam());
    }

    /**
     * 保存文件信息并重命名（File）
     */
    public <T extends Attachment> T save(File file, String name, String path, FileParam param) throws IOException {
        return (T) getAttachHandler(param.getSourceType()).save(file, name, path, param.toAttachmentParam());
    }

    public Attachment updateAttachment(Attachment attachment) {
        attachment.handleGenre();
        if (AttachHandler.ATTACHMENT_SOURCE.equalsIgnoreCase(attachment.getSource())) {
            Attach model = JSON.parseObject(JSON.toJSONString(attachment), Attach.class);
            return attachHandler.update(model);
        } else if (CompressHandler.ATTACHMENT_SOURCE.equalsIgnoreCase(attachment.getSource())) {
            Compress model = JSON.parseObject(JSON.toJSONString(attachment), Compress.class);
            return compressHandler.update(model);
        } else if (ResourcesHandler.ATTACHMENT_SOURCE.equalsIgnoreCase(attachment.getSource())) {
            Resources model = JSON.parseObject(JSON.toJSONString(attachment), Resources.class);
            return resourcesHandler.update(model);
        }
        return null;
    }

    public Attachment createAttachment(Attachment attachment) {
        attachment.handleGenre();
        attachment.setId(null);
        if (AttachHandler.ATTACHMENT_SOURCE.equalsIgnoreCase(attachment.getSource())) {
            Attach model = JSON.parseObject(JSON.toJSONString(attachment), Attach.class);
            return attachHandler.create(model);
        } else if (CompressHandler.ATTACHMENT_SOURCE.equalsIgnoreCase(attachment.getSource())) {
            Compress model = JSON.parseObject(JSON.toJSONString(attachment), Compress.class);
            return compressHandler.create(model);
        } else if (ResourcesHandler.ATTACHMENT_SOURCE.equalsIgnoreCase(attachment.getSource())) {
            Resources model = JSON.parseObject(JSON.toJSONString(attachment), Resources.class);
            return resourcesHandler.create(model);
        }
        return null;
    }

    public void updateId(String tableType, String sourceId, String targetId, boolean isDelete) {
        if (AttachHandler.ATTACHMENT_SOURCE.equalsIgnoreCase(tableType)) {
            attachHandler.updateId(sourceId, targetId, isDelete);
        } else if (CompressHandler.ATTACHMENT_SOURCE.equalsIgnoreCase(tableType)) {
            compressHandler.updateId(sourceId, targetId, isDelete);
        } else if (ResourcesHandler.ATTACHMENT_SOURCE.equalsIgnoreCase(tableType)) {
            resourcesHandler.updateId(sourceId, targetId, isDelete);
        }
    }


    /**
     * 根据附件ID和是否为缩略图获取附件信息
     *
     * @param id          附件ID
     * @param isThumbnail 是否为缩略图
     * @return 返回对应的附件对象，如果不存在则返回null
     */
    public Attachment getAttachment(String id, boolean isThumbnail) {
        return getAttachHandler(AttachHandler.ATTACHMENT_SOURCE).single(id, isThumbnail);
    }

    /**
     * 获取附件列表
     *
     * @param params 查询参数，可以是文件类型、文件大小等
     * @return 返回符合条件的附件列表
     */
    public List<Attachment> getAttachments(Map<String, Object> params) {
        return getAttachHandler(AttachHandler.ATTACHMENT_SOURCE).list(params);
    }

    public long countAttachments(Map<String, Object> params) {
        return getAttachHandler(AttachHandler.ATTACHMENT_SOURCE).count(params);
    }

    public List<Attachment> getAttachments(String attachmentIds) {
        return getAttachHandler(AttachHandler.ATTACHMENT_SOURCE).list(attachmentIds);
    }

    public void updateChildThumbnail(String handlerType, String parentId, List<String> ids) {
        getAttachHandler(handlerType).updateChildThumbnail(parentId, ids);
    }

    /**
     * 删除附件
     *
     * @param id        附件ID
     * @param isRemoved 是否真正从数据库中删除附件，true表示删除，false表示标记为已删除
     */
    public void delete(String id, Boolean isRemoved) {
        AttachmentHandler<?> attachmentHandler = getAttachHandler(AttachHandler.ATTACHMENT_SOURCE);
        if (Strings.isNotBlank(id)) {
            // 查询附件的原图和缩略图
            List<Attachment> attachments = attachmentHandler.list("pids", id);
            for (Attachment attachment : attachments) {
                if (isRemoved && Strings.isBlank(attachment.getObjectId())) {
                    // 引用附件，仅需删除附件信息
                    if (attachment.getGenre() != null && attachment.getGenre().contains(FileGenreEnum.Quote.name())) {
                        isDelete(attachment);
                    } else {
                        // 物理删除附件的原图和缩略图（本地存储）
                        delete(attachment);
                        // 将同一个path的其他附件也删除
                        deleteSamePaths(attachmentHandler, attachment);
                    }
                } else {
                    // 逻辑删除附件的原图和缩略图
                    isDelete(attachment);
                }
            }
        }
    }

    public void deleteSamePaths(AttachmentHandler<?> attachmentHandler, Attachment attachment) {
        List<Attachment> quoteAttachments = attachmentHandler.list(SqlParams.map("pathUrl", attachment.getPath()));
        if (!quoteAttachments.isEmpty()) {
            for (Attachment quoteAttachment : quoteAttachments) {
                isDelete(quoteAttachment);
            }
        }
    }

    public void delete(Attachment attachment) {
        if (AttachHandler.ATTACHMENT_SOURCE.equalsIgnoreCase(attachment.getSource())) {
            Attach model = JSON.parseObject(JSON.toJSONString(attachment), Attach.class);
            attachHandler.delete(model);
        } else if (CompressHandler.ATTACHMENT_SOURCE.equalsIgnoreCase(attachment.getSource())) {
            Compress model = JSON.parseObject(JSON.toJSONString(attachment), Compress.class);
            compressHandler.delete(model);
        } else if (ResourcesHandler.ATTACHMENT_SOURCE.equalsIgnoreCase(attachment.getSource())) {
            Resources model = JSON.parseObject(JSON.toJSONString(attachment), Resources.class);
            resourcesHandler.delete(model);
        }
    }

    public void isDelete(Attachment attachment) {
        if (AttachHandler.ATTACHMENT_SOURCE.equalsIgnoreCase(attachment.getSource())) {
            Attach model = JSON.parseObject(JSON.toJSONString(attachment), Attach.class);
            attachHandler.isDelete(model);
        } else if (CompressHandler.ATTACHMENT_SOURCE.equalsIgnoreCase(attachment.getSource())) {
            Compress model = JSON.parseObject(JSON.toJSONString(attachment), Compress.class);
            compressHandler.isDelete(model);
        } else if (ResourcesHandler.ATTACHMENT_SOURCE.equalsIgnoreCase(attachment.getSource())) {
            Resources model = JSON.parseObject(JSON.toJSONString(attachment), Resources.class);
            resourcesHandler.isDelete(model);
        }
    }

    private AttachmentHandler<?> getAttachHandler(String handlerType) {
        if (ResourcesHandler.ATTACHMENT_SOURCE.equalsIgnoreCase(handlerType)) {
            return resourcesHandler;
        } else if (CompressHandler.ATTACHMENT_SOURCE.equalsIgnoreCase(handlerType)) {
            return compressHandler;
        } else {
            return attachHandler;
        }
    }
}

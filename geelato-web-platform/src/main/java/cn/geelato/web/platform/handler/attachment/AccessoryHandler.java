package cn.geelato.web.platform.handler.attachment;

import cn.geelato.web.platform.m.base.entity.Attach;
import cn.geelato.web.platform.m.base.entity.Attachment;
import cn.geelato.web.platform.m.base.entity.Resources;
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
    private final ResourcesHandler resourcesHandler;

    public AccessoryHandler(AttachHandler attachHandler, ResourcesHandler resourcesHandler) {
        this.attachHandler = attachHandler;
        this.resourcesHandler = resourcesHandler;
    }

    /**
     * 上传到本地磁盘（MultipartFile）
     */
    public Attachment upload(String handlerType, MultipartFile file, String path, String appId, String tenantCode, String genre, boolean isThumbnail, Integer dimension) throws IOException {
        return getAttachHandler(handlerType).upload(file, path, appId, tenantCode, genre, isThumbnail, dimension);
    }

    /**
     * 上传到本地磁盘（base64字符串文件）
     */
    public Attachment upload(String handlerType, String base64String, String name, String path, String appId, String tenantCode, String genre, boolean isThumbnail, Integer dimension) throws IOException {
        return getAttachHandler(handlerType).upload(base64String, name, path, appId, tenantCode, genre, isThumbnail, dimension);
    }

    /**
     * 保存文件信息（MultipartFile）
     */
    public Attachment save(String handlerType, MultipartFile file, String path, String objectId, String genre, String appId, String tenantCode) {
        return getAttachHandler(handlerType).save(file, path, objectId, genre, appId, tenantCode);
    }

    /**
     * 保存文件信息（File）
     */
    public Attachment save(String handlerType, File file, String path, String objectId, String genre, String appId, String tenantCode) throws IOException {
        return getAttachHandler(handlerType).save(file, path, objectId, genre, appId, tenantCode);
    }

    /**
     * 保存文件信息并重命名（File）
     */
    public Attachment save(String handlerType, File file, String name, String path, String objectId, String genre, String appId, String tenantCode) throws IOException {
        return getAttachHandler(handlerType).save(file, name, path, objectId, genre, appId, tenantCode);
    }

    /**
     * 更新附件或资源ID
     *
     * @param handlerType 处理器类型
     * @param sourceId    原附件ID
     * @param targetId    目标附件ID
     * @return 更新后的附件ID
     */
    public String updateId(String handlerType, String sourceId, String targetId) {
        return getAttachHandler(handlerType).updateId(sourceId, targetId);
    }

    /**
     * 更新缩略图ID
     *
     * @param handlerType 处理器类型
     * @param sourceId    源ID
     * @param targetId    目标ID
     * @return 更新后的ID
     */
    public String updateThumbnailId(String handlerType, String sourceId, String targetId) {
        return getAttachHandler(handlerType).updateId(attachHandler.setThumbnailId(sourceId), targetId);
    }

    /**
     * 更新删除标识的ID
     *
     * @param handlerType 处理器类型
     * @param sourceId    源ID
     * @return 更新后的ID
     */
    public String updateDeleteId(String handlerType, String sourceId) {
        return getAttachHandler(handlerType).updateId(attachHandler.setDeleteId(sourceId), sourceId);
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

    /**
     * 删除附件
     *
     * @param id        附件ID
     * @param isRemoved 是否真正从数据库中删除附件，true表示删除，false表示标记为已删除
     */
    public void delete(String id, Boolean isRemoved) {
        AttachmentHandler<?> attachmentHandler = getAttachHandler(AttachHandler.ATTACHMENT_SOURCE);
        String ids = attachmentHandler.getPriAndThuId(id);
        if (Strings.isNotBlank(ids)) {
            // 查询附件的原图和缩略图
            List<Attachment> attachments = attachmentHandler.list(ids);
            for (Attachment attachment : attachments) {
                // 逻辑删除附件的原图和缩略图
                if (AttachHandler.ATTACHMENT_SOURCE.equalsIgnoreCase(attachment.getSource())) {
                    Attach attach = JSON.parseObject(JSON.toJSONString(attachment), Attach.class);
                    attachHandler.isDelete(attach);
                } else if (ResourcesHandler.ATTACHMENT_SOURCE.equalsIgnoreCase(attachment.getSource())) {
                    Resources resources = JSON.parseObject(JSON.toJSONString(attachment), Resources.class);
                    resourcesHandler.isDelete(resources);
                } else {
                    continue;
                }
                // 物理删除附件的原图和缩略图（本地存储）
                if (isRemoved && Strings.isBlank(attachment.getObjectId())) {
                    // 如果附件没有关联对象，则删除本地文件
                    attachmentHandler.delete(attachment.getPath());
                    // 更新附件的删除标识
                    updateDeleteId(attachment.getSource(), attachment.getId());
                }
            }
        }
    }

    private AttachmentHandler<?> getAttachHandler(String handlerType) {
        if (ResourcesHandler.ATTACHMENT_SOURCE.equalsIgnoreCase(handlerType)) {
            return resourcesHandler;
        } else {
            return attachHandler;
        }
    }
}

package cn.geelato.web.platform.m.file.handler;

import cn.geelato.core.orm.Dao;
import cn.geelato.utils.FileUtils;
import cn.geelato.utils.StringUtils;
import cn.geelato.utils.ThumbnailUtils;
import cn.geelato.web.platform.m.base.service.UploadService;
import cn.geelato.web.platform.m.file.entity.Attachment;
import cn.geelato.web.platform.m.file.param.AttachmentParam;
import cn.geelato.web.platform.m.file.param.ThumbnailParam;
import cn.geelato.web.platform.m.file.utils.AttachmentParamUtils;
import com.alibaba.fastjson2.JSON;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public abstract class AttachmentHandler<E extends Attachment> {
    @Autowired
    @Qualifier("primaryDao")
    public Dao dao;

    /**
     * 保存文件并返回附件对象
     *
     * @param file 要上传的文件
     * @param path 文件路径（地址或oss）
     * @return 保存后的附件对象
     */
    public abstract E save(MultipartFile file, String path, AttachmentParam param);

    public abstract E save(File file, String path, AttachmentParam param) throws IOException;

    /**
     * 设置文件名，保存文件并返回附件对象
     */
    public abstract E save(File file, String name, String path, AttachmentParam param) throws IOException;

    /**
     * 根据提供的文件信息构建一个附件对象
     *
     * @param file 要上传的文件(name,type,size)
     * @param path 文件保存的路径
     * @return 构建好的附件对象
     */
    public abstract E build(MultipartFile file, String path, AttachmentParam param);

    public abstract E build(File file, String path, AttachmentParam param) throws IOException;

    /**
     * 根据提供的文件信息构建一个附件对象，并设置文件名
     */
    public abstract E build(File file, String name, String path, AttachmentParam param) throws IOException;

    public E build(E attachment, String path, AttachmentParam param) {
        attachment.setPath(path);
        return param.toAttachment(attachment);
    }

    /**
     * 上传文件并保存到磁盘和数据库，同时可选生成缩略图
     *
     * @param file 要上传的文件
     * @param path 文件保存的路径
     * @return 上传后的附件对象
     * @throws IOException 如果在文件处理过程中发生I/O错误
     */
    public abstract E upload(MultipartFile file, String path, ThumbnailParam param) throws IOException;

    public abstract E upload(String base64String, String name, String path, ThumbnailParam param) throws IOException;

    /**
     * 为给定的附件生成缩略图
     *
     * @param source 要生成缩略图的原始附件对象
     * @return 如果生成了缩略图，则返回新的缩略图附件对象，否则返回null
     * @throws IOException 如果在生成缩略图或保存缩略图时发生I/O错误
     */
    public abstract E thumbnail(E source, ThumbnailParam param) throws IOException;

    /**
     * 为给定的附件生成缩略图
     *
     * @param source           要生成缩略图的原始附件对象
     * @param attachmentSource 附件来源，例如：avatar,convert等
     * @param dimension        缩略图的尺寸（宽度或高度），如果为null则不进行尺寸判断
     * @return 如果生成了缩略图，则返回新的缩略图附件对象，否则返回null
     * @throws IOException 如果在生成缩略图或保存缩略图时发生I/O错误
     */
    public E createThumbnail(E source, String attachmentSource, Integer dimension, Double thumbScale) throws IOException {
        File sourceFile = new File(source.getPath());
        if (ThumbnailUtils.isThumbnail(sourceFile, dimension)) {
            String path = UploadService.getSavePath(UploadService.ROOT_DIRECTORY, attachmentSource, source.getTenantCode(), source.getAppId(), source.getName(), true);
            File file = new File(path);
            ThumbnailUtils.thumbnail(sourceFile, file, dimension, thumbScale);
            if (!file.exists()) {
                throw new RuntimeException("thumbnail save failed");
            }
            String genre = StringUtils.splice(",", source.getGenre(), ThumbnailUtils.THUMBNAIL_GENRE);
            AttachmentParam attachmentParam = AttachmentParamUtils.byThumbnail(genre, source.getAppId(), source.getTenantCode());
            return build(file, source.getName(), path, attachmentParam);
        }
        return null;
    }

    /**
     * 设置缩略图的ID
     *
     * @param id 原始图片的ID
     * @return 缩略图的ID
     */
    public String setThumbnailId(String id) {
        return id + ThumbnailUtils.THUMBNAIL_SUFFIX;
    }

    /**
     * 设置原始图片ID
     *
     * @param targetId 目标ID，通常为包含缩略图后缀的字符串
     * @return 去除缩略图后缀后的原始图片ID
     */
    public String setPrimevalId(String targetId) {
        return targetId.substring(0, targetId.lastIndexOf(ThumbnailUtils.THUMBNAIL_SUFFIX));
    }

    /**
     * 设置删除标识的ID（删除文本后）
     *
     * @param id 需要设置删除标识的ID
     * @return 返回添加了删除标识的ID
     */
    public String setDeleteId(String id) {
        return id + "_delete";
    }

    /**
     * 获取原始图片ID和缩略图ID
     *
     * @param id 图片ID，可以是原始图片ID或缩略图ID
     * @return 以逗号分隔的原始图片ID和缩略图ID字符串，如果id为空或无效则返回空字符串
     */
    public String getPriAndThuId(String id) {
        if (Strings.isNotBlank(id)) {
            if (id.endsWith(ThumbnailUtils.THUMBNAIL_SUFFIX)) {
                return StringUtils.splice(",", id, setPrimevalId(id));
            } else {
                return StringUtils.splice(",", id, setThumbnailId(id));
            }
        }
        return "";
    }


    /**
     * 更新指定目标ID为新的ID
     *
     * @param updateId 新的ID，用于替换目标ID
     * @param sourceId 目标ID，需要被更新的ID
     * @return 更新后的ID字符串
     */
    public abstract String updateId(String updateId, String sourceId);

    /**
     * 获取附件信息
     * <p>
     * 根据附件ID和是否获取缩略图的标志，获取对应的附件信息。
     *
     * @param id          附件ID
     * @param isThumbnail 是否获取缩略图
     * @return 返回对应的附件信息对象，如果未找到则返回null
     */
    public Attachment single(String id, boolean isThumbnail) {
        Attachment primeval = null;
        Attachment thumbnail = null;
        String ids = getPriAndThuId(id);
        if (Strings.isNotBlank(ids)) {
            List<Attachment> attachments = list(ids);
            for (Attachment attachment : attachments) {
                if (attachment.getId().endsWith(ThumbnailUtils.THUMBNAIL_SUFFIX)) {
                    thumbnail = attachment;
                } else {
                    primeval = attachment;
                }
            }
        }
        // 根据是否需要缩略图返回对应的附件信息
        return isThumbnail && thumbnail != null ? thumbnail : primeval;
    }

    public List<Attachment> list(String attachmentIds) {
        return list("ids", attachmentIds);
    }

    public List<Attachment> list(String fieldName, Object value) {
        Map<String, Object> params = new HashMap<>();
        params.put(fieldName, value);
        return list(params);
    }

    /**
     * 通过参数查询附件列表
     *
     * @param params 查询参数
     * @return 返回附件列表
     */
    public List<Attachment> list(Map<String, Object> params) {
        if (params != null && !params.isEmpty()) {
            List<Map<String, Object>> mapList = dao.queryForMapList("platform_attachment_by_more", params);
            if (mapList != null && !mapList.isEmpty()) {
                return JSON.parseArray(JSON.toJSONString(mapList), Attachment.class);
            }
        }
        return new ArrayList<>();
    }


    /**
     * 逻辑删除文件
     *
     * @param attachment 要判断的附件对象
     */
    public abstract void isDelete(E attachment);

    public abstract void delete(E attachment);


    /**
     * 删除文件
     *
     * @param path 文件的路径
     * @return 如果文件删除成功则返回true，否则返回false
     */
    public boolean deleteFile(String path) {
        if (StringUtils.isNotBlank(path)) {
            File file = FileUtils.pathToFile(path);
            if (file != null) {
                return file.delete();
            }
        }
        return true;
    }

}

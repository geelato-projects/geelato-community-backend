package cn.geelato.web.platform.m.file.handler;

import cn.geelato.core.orm.Dao;
import cn.geelato.utils.FileUtils;
import cn.geelato.utils.StringUtils;
import cn.geelato.utils.ThumbnailUtils;
import cn.geelato.utils.entity.Resolution;
import cn.geelato.web.platform.m.base.service.UploadService;
import cn.geelato.web.platform.m.file.entity.Attachment;
import cn.geelato.web.platform.m.file.param.AttachmentParam;
import cn.geelato.web.platform.m.file.param.ThumbnailParam;
import cn.geelato.web.platform.m.file.param.ThumbnailResolution;
import com.alibaba.fastjson2.JSON;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.*;

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

    public abstract E thumbAndSave(File file, String name, String path, ThumbnailParam param) throws IOException;

    public List<ThumbnailResolution> createThumbnail(File file, String fileName, String attachmentSource, String appId, String tenantCode, String dimension, String thumbScale) throws IOException {
        List<ThumbnailResolution> thumbnailResolutions = new ArrayList<>();
        // 根据原始图片的尺寸和缩略图比例，计算需要生成的缩略图的分辨率列表
        List<Resolution> thumbResolutions = ThumbnailUtils.resolution(file, dimension, thumbScale);
        if (thumbResolutions != null && !thumbResolutions.isEmpty()) {
            for (Resolution resolution : thumbResolutions) {
                String thumbPath = UploadService.getSavePath(UploadService.ROOT_DIRECTORY, attachmentSource, tenantCode, appId, fileName, true);
                File thumbFile = new File(thumbPath);
                ThumbnailUtils.thumbnail(file, thumbFile, resolution);
                if (thumbFile == null || !thumbFile.exists()) {
                    throw new RuntimeException("thumbnail save failed");
                }
                thumbnailResolutions.add(new ThumbnailResolution(resolution, thumbFile, thumbPath));
            }
        }
        return thumbnailResolutions;
    }

    public abstract void updateChildThumbnail(String parentId, List<String> updateIds);

    public void updateChildThumbnail(String updatePidSql, String parentId, List<String> updateIds) {
        if (parentId != null && updateIds != null) {
            for (String id : updateIds) {
                if (parentId.equals(id)) {
                    continue;
                }
                dao.getJdbcTemplate().update(updatePidSql, parentId, id);
            }
        }
    }

    public E getParentThumbnail(Map<Integer, E> thumbMap) {
        if (!thumbMap.isEmpty()) {
            Optional<Integer> optional = thumbMap.keySet().stream().sorted(Comparator.reverseOrder()).findFirst();
            return thumbMap.get(optional.get());
        }
        return null;
    }


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
        if (Strings.isNotBlank(id)) {
            List<Attachment> attachments = list(id);
            primeval = attachments.stream().findFirst().orElse(null);
        }
        Attachment thumbnail = null;
        if (isThumbnail && Strings.isNotBlank(id)) {
            List<Attachment> attachments = list("pid", id);
            thumbnail = minResolutions(attachments);
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

    public Attachment minResolutions(List<Attachment> thumbnail) {
        if (thumbnail != null && !thumbnail.isEmpty()) {
            Map<String, Attachment> thumbnailMap = new HashMap<>();
            for (Attachment attachment : thumbnail) {
                if (Strings.isNotBlank(attachment.getResolution())) {
                    thumbnailMap.put(attachment.getResolution(), attachment);
                }
            }
            if (!thumbnailMap.isEmpty()) {
                Resolution resolution = Resolution.min(thumbnailMap.keySet());
                return thumbnailMap.get(resolution == null ? "0x0" : resolution.getProduct());
            }
        }
        return null;
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

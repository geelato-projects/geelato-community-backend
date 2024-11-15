package cn.geelato.web.platform.m.base.service;

import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.utils.ImageUtils;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.enums.AttachmentSourceEnum;
import cn.geelato.web.platform.m.base.entity.Attach;
import cn.geelato.web.platform.m.base.entity.Resources;
import com.alibaba.fastjson2.JSON;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author diabl
 */
@Component
public class AttachService extends BaseService {

    @Lazy
    @Autowired
    private ResourcesService resourcesService;

    /**
     * 获取单条数据
     * <p>
     * 根据给定的ID，从数据库中查询并返回对应的Attach对象。
     *
     * @param id 要查询的数据的ID
     * @return 返回查询到的Attach对象，如果未找到对应的数据则返回null
     */
    public Attach getModel(String id) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        Map<String, Object> map = dao.queryForMap("platform_attachment_by_more", params);
        return JSON.parseObject(JSON.toJSONString(map), Attach.class);
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
    public Attach getModelThumbnail(String id, boolean isThumbnail) {
        List<String> ids = new ArrayList<>();
        if (id.endsWith(ImageUtils.THUMBNAIL_SUFFIX)) {
            ids.add(id);
            ids.add(id.substring(0, id.lastIndexOf(ImageUtils.THUMBNAIL_SUFFIX)));
        } else {
            ids.add(id);
            if (isThumbnail) {
                ids.add(id + ImageUtils.THUMBNAIL_SUFFIX);
            }
        }
        Map<String, Object> params = new HashMap<>();
        params.put("id", StringUtils.join(ids, ","));
        List<Map<String, Object>> mapList = dao.queryForMapList("platform_attachment_by_more", params);
        if (mapList != null && mapList.size() > 0) {
            if (mapList.size() == 1) {
                return JSON.parseObject(JSON.toJSONString(mapList.get(0)), Attach.class);
            } else if (mapList.size() > 1) {
                for (Map<String, Object> map : mapList) {
                    if (map.get("id").toString().endsWith(ImageUtils.THUMBNAIL_SUFFIX)) {
                        return JSON.parseObject(JSON.toJSONString(map), Attach.class);
                    }
                }
            }
        }
        return null;
    }

    /**
     * 批量查询附件信息
     * <p>
     * 根据提供的参数批量查询附件信息，并返回查询结果列表。
     *
     * @param params 查询参数，包含查询所需的条件
     * @return 返回查询到的附件信息列表，每个元素都是Attach类的实例
     */
    public List<Attach> list(Map<String, Object> params) {
        List<Map<String, Object>> mapList = dao.queryForMapList("platform_attachment_by_more", params);
        return JSON.parseArray(JSON.toJSONString(mapList), Attach.class);
    }

    /**
     * 逻辑删除方法。
     * <p>
     * 根据模型的来源进行不同的删除操作。
     * 如果模型来源于平台附件，则调用父类的逻辑删除方法；
     * 否则，将模型属性复制到Resources对象中，并调用resourcesService的逻辑删除方法。
     *
     * @param model 要进行逻辑删除的模型对象
     */
    public void isDeleteModel(Attach model) {
        if (AttachmentSourceEnum.PLATFORM_ATTACH.getValue().equals(model.getSource())) {
            super.isDeleteModel(model);
        } else {
            Resources resources = UploadService.copyProperties(model, Resources.class);
            resourcesService.isDeleteModel(resources);
        }
    }

    /**
     * 删除实体文件
     * <p>
     * 删除与给定实体模型对应的文件。
     *
     * @param model 实体模型，包含要删除文件的路径信息
     * @return 如果文件删除成功，则返回true；如果文件不存在或删除失败，则返回true（假设不存在或删除失败不影响方法的主要逻辑）
     */
    public boolean deleteFile(Attach model) {
        Assert.notNull(model, ApiErrorMsg.IS_NULL);
        if (Strings.isNotBlank(model.getPath())) {
            File file = new File(model.getPath());
            if (file.exists()) {
                return file.delete();
            }
        }

        return true;
    }

    /**
     * 通过文件保存附件。
     *
     * @param file       要保存的文件
     * @param name       文件的名称
     * @param genre      文件的类型
     * @param appId      应用的ID
     * @param tenantCode 租户的代码
     * @return 返回保存的附件对象
     * @throws IOException 如果在文件操作过程中发生I/O错误，则抛出此异常
     */
    public Attach saveByFile(File file, String name, String genre, String appId, String tenantCode) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
        Attach attach = new Attach();
        attach.setTenantCode(tenantCode);
        attach.setAppId(appId);
        attach.setName(name);
        attach.setType(Files.probeContentType(file.toPath()));
        attach.setGenre(genre);
        attach.setSize(attributes.size());
        attach.setPath(file.getPath());
        return this.createModel(attach);
    }
}

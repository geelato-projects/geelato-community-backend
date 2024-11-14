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
     * 单条数据获取
     *
     * @param id
     * @return
     */
    public Attach getModel(String id) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        Map<String, Object> map = dao.queryForMap("platform_attachment_by_more", params);
        return JSON.parseObject(JSON.toJSONString(map), Attach.class);
    }

    /**
     * 获取附件信息
     *
     * @param id          附件id
     * @param isThumbnail 是否获取缩略图
     * @return
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
     * 批量查询
     *
     * @param params
     * @return
     */
    public List<Attach> list(Map<String, Object> params) {
        List<Map<String, Object>> mapList = dao.queryForMapList("platform_attachment_by_more", params);
        return JSON.parseArray(JSON.toJSONString(mapList), Attach.class);
    }

    /**
     * 逻辑删除
     *
     * @param model
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
     *
     * @param model
     * @return
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

    public static void main(String[] args) throws IOException {
        File file = new File("/upload/attach/geelato/1976169388038462609/2024/11/14/15/32/5693093797047701504.png");
        System.out.println(file.getAbsolutePath());
        System.out.println(file.getCanonicalPath());
        System.out.println(file.getPath());
        System.out.println(file.getName());
        System.out.println(file.getParent());
    }

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

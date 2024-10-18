package cn.geelato.web.platform.m.base.service;

import cn.geelato.lang.constants.ApiErrorMsg;
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

    public Attach saveByFile(File file, String name, String genre, String appId, String tenantCode) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
        Attach attach = new Attach();
        attach.setTenantCode(tenantCode);
        attach.setAppId(appId);
        attach.setName(name);
        attach.setType(Files.probeContentType(file.toPath()));
        attach.setGenre(genre);
        attach.setSize(attributes.size());
        attach.setPath(file.getAbsolutePath());
        return this.createModel(attach);
    }
}

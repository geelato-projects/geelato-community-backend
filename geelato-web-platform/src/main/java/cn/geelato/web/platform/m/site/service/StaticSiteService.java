package cn.geelato.web.platform.m.site.service;

import cn.geelato.web.platform.m.base.service.BaseService;
import cn.geelato.web.platform.m.base.service.UploadService;
import cn.geelato.web.platform.m.site.entity.StaticSite;
import cn.geelato.web.platform.m.site.utils.FolderUtils;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class StaticSiteService extends BaseService {

    public StaticSite createModel(StaticSite model) {
        // 创建静态站点
        model = super.createModel(model);
        // 创建文件夹
        FolderUtils.create(UploadService.ROOT_SITE_DIRECTORY, model.getId());

        return model;
    }

    public StaticSite updateModel(StaticSite model) {
        // 更新静态站点
        model = super.updateModel(model);
        // 文件夹不存在，创建
        File folder = new File(UploadService.ROOT_SITE_DIRECTORY, model.getId());
        if (!folder.exists()) {
            FolderUtils.create(UploadService.ROOT_SITE_DIRECTORY, model.getId());
        }

        return model;
    }

    public void isDeleteModel(StaticSite model) {
        // 删除文件夹
        FolderUtils.delete(UploadService.ROOT_SITE_DIRECTORY, model.getId());
        // 删除静态站点
        super.isDeleteModel(model);
    }
}

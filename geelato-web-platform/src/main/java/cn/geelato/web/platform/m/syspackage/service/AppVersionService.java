package cn.geelato.web.platform.m.syspackage.service;

import cn.geelato.web.platform.m.base.service.BaseService;
import cn.geelato.web.platform.m.syspackage.entity.AppVersion;
import cn.geelato.web.platform.m.syspackage.enums.PackageSourceEnum;
import cn.geelato.web.platform.m.syspackage.enums.PackageStatusEnum;
import org.springframework.stereotype.Component;

@Component
public class AppVersionService extends BaseService {

    public AppVersion getAppVersionByVersion(String version) {
        return dao.queryForObject(AppVersion.class, "version", version);
    }

    public void createByUploadApp(String packagePath, String version, String appId) {
        AppVersion av = new AppVersion();
        av.setPackagePath(packagePath);
        av.setVersion(version);
        av.setPackageSource(PackageSourceEnum.UPLOAD.getValue());
        av.setStatus(PackageStatusEnum.DRAFT.getValue());
        av.setAppId(appId);
        av.setTenantCode(getSessionTenantCode());

        super.createModel(av);
    }
}

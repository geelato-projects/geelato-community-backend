package cn.geelato.web.platform.m.syspackage.service;

import cn.geelato.web.platform.m.base.service.BaseService;
import cn.geelato.web.platform.m.syspackage.entity.AppVersion;
import org.springframework.stereotype.Component;

@Component
public class AppVersionService  extends BaseService {

    public AppVersion getAppVersionByVersion(String version){
        return dao.queryForObject(AppVersion.class,"version",version);
    }
}

package cn.geelato.web.platform.m.base.service;

import cn.geelato.web.platform.m.base.entity.App;
import cn.geelato.web.platform.m.base.entity.AppRestfulMap;
import cn.geelato.web.platform.m.base.entity.CustomRestful;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * @author diabl
 */
@Component
public class AppRestfulMapService extends BaseService {
    private final Logger logger = LoggerFactory.getLogger(AppRestfulMapService.class);

    @Lazy
    @Autowired
    private AppService appService;
    @Lazy
    @Autowired
    private RestfulService restfulService;


    public void after(AppRestfulMap form) {
        if (Strings.isNotBlank(form.getAppId())) {
            App app = appService.getModel(App.class, form.getAppId());
            form.setAppName(app.getName());
        }
        if (Strings.isNotBlank(form.getRestfulId())) {
            CustomRestful customRestful = restfulService.getModel(CustomRestful.class, form.getRestfulId());
            form.setRestfulTitle(customRestful.getTitle());
            form.setRestfulKey(customRestful.getKeyName());
            if (Strings.isBlank(form.getRestfulAppId())) {
                form.setRestfulAppId(customRestful.getAppId());
            }
        }
    }
}
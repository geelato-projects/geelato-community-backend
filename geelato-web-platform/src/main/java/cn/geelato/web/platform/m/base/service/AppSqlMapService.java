package cn.geelato.web.platform.m.base.service;

import cn.geelato.meta.App;
import cn.geelato.meta.AppSqlMap;
import cn.geelato.meta.CustomSql;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * @author diabl
 */
@Component
@Slf4j
public class AppSqlMapService extends BaseService {
    @Lazy
    @Autowired
    private AppService appService;
    @Lazy
    @Autowired
    private SqlService sqlService;


    public void after(AppSqlMap form) {
        if (Strings.isNotBlank(form.getAppId())) {
            App app = appService.getModel(App.class, form.getAppId());
            form.setAppName(app.getName());
        }
        if (Strings.isNotBlank(form.getSqlId())) {
            CustomSql customSql = sqlService.getModel(CustomSql.class, form.getSqlId());
            form.setSqlTitle(customSql.getTitle());
            form.setSqlKey(customSql.getKeyName());
            if (Strings.isBlank(form.getSqlAppId())) {
                form.setSqlAppId(customSql.getAppId());
            }
        }
    }
}

package cn.geelato.web.platform.srv.platform.service;

import cn.geelato.web.platform.srv.model.service.DevTableService;
import cn.geelato.meta.Permission;
import cn.geelato.web.platform.srv.security.service.PermissionService;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.meta.App;
import cn.geelato.meta.AppTableMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @author diabl
 */
@Component
public class AppTableMapService extends BaseService {
    @Lazy
    @Autowired
    private AppService appService;
    @Lazy
    @Autowired
    private DevTableService devTableService;
    @Lazy
    @Autowired
    private PermissionService permissionService;

    public void after(AppTableMap form) {
        if (Strings.isNotBlank(form.getAppId())) {
            App app = appService.getModel(App.class, form.getAppId());
            form.setAppName(app.getName());
        }
        if (Strings.isNotBlank(form.getTableId())) {
            TableMeta tableMeta = devTableService.getModel(TableMeta.class, form.getTableId());
            form.setTableName(tableMeta.getEntityName());
            form.setTableTitle(tableMeta.getTitle());
            if (Strings.isBlank(form.getTableAppId())) {
                form.setTableAppId(tableMeta.getAppId());
            }
        }
        if (Strings.isNotBlank(form.getPermissionId())) {
            FilterGroup filter = new FilterGroup();
            filter.addFilter("id", FilterGroup.Operator.in, form.getPermissionId());
            List<Permission> list = permissionService.queryModel(Permission.class, filter);
            List<String> names = new ArrayList<>();
            for (Permission permission : list) {
                if (Strings.isNotBlank(permission.getDescription())) {
                    names.add(String.format("%s（%s）", permission.getName(), permission.getDescription()));
                } else {
                    names.add(String.format("%s（%s）", permission.getName(), permission.getCode()));
                }
            }
            form.setPermissionName(String.join("\n\t", names));
        }
    }
}

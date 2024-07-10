package cn.geelato.web.platform.m.base.service;

import cn.geelato.web.platform.m.model.service.DevViewService;
import cn.geelato.web.platform.m.security.entity.Permission;
import cn.geelato.web.platform.m.security.service.PermissionService;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.meta.model.view.TableView;
import cn.geelato.web.platform.m.base.entity.App;
import cn.geelato.web.platform.m.base.entity.AppViewMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @author diabl
 * @date 2024/4/17 16:24
 */
@Component
public class AppViewMapService extends BaseService {
    @Lazy
    @Autowired
    private AppService appService;
    @Lazy
    @Autowired
    private DevViewService devViewService;
    @Lazy
    @Autowired
    private PermissionService permissionService;

    public void after(AppViewMap form) {
        if (Strings.isNotBlank(form.getAppId())) {
            App app = appService.getModel(App.class, form.getAppId());
            form.setAppName(app.getName());
        }
        if (Strings.isNotBlank(form.getViewId())) {
            TableView tableView = devViewService.getModel(TableView.class, form.getViewId());
            form.setTableName(tableView.getEntityName());
            form.setViewName(tableView.getViewName());
            form.setViewTitle(tableView.getTitle());
            if (Strings.isBlank(form.getViewAppId())) {
                form.setViewAppId(tableView.getAppId());
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

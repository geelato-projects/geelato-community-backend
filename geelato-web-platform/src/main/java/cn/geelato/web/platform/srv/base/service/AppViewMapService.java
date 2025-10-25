package cn.geelato.web.platform.srv.base.service;

import cn.geelato.web.platform.srv.model.service.DevViewService;
import cn.geelato.meta.Permission;
import cn.geelato.web.platform.srv.security.service.PermissionService;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.meta.model.view.TableView;
import cn.geelato.meta.App;
import cn.geelato.meta.AppViewMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @author diabl
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

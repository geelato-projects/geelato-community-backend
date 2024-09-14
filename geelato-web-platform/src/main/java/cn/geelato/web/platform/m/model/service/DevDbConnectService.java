package cn.geelato.web.platform.m.model.service;

import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.meta.model.connect.ConnectMeta;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.m.base.service.BaseService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author diabl
 */
@Component
public class DevDbConnectService extends BaseService {

    public void batchCreate(String appId, List<String> connectIds) {
        FilterGroup filter = new FilterGroup();
        filter.addFilter("id", FilterGroup.Operator.in, StringUtils.join(connectIds, ","));
        List<ConnectMeta> connectMetaList = this.queryModel(ConnectMeta.class, filter);
        if (connectMetaList != null && !connectMetaList.isEmpty()) {
            for (ConnectMeta connectMeta : connectMetaList) {
                connectMeta.setId(null);
                connectMeta.setDbUserName(null);
                connectMeta.setDbPassword(null);
                connectMeta.setAppId(appId);
                this.createModel(connectMeta);
            }
        }
    }
}

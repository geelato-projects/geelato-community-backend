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

    /**
     * 检查数据库连接是否存在
     *
     * @param meta
     * @return
     */
    public boolean isExist(ConnectMeta meta) {
        return this.isExist(meta.getId(), meta.getDbType(), meta.getDbHostnameIp(), meta.getDbPort(), meta.getDbName());
    }

    /**
     * 检查数据库连接是否存在
     *
     * @param id
     * @param type
     * @param hostnameIp
     * @param port
     * @param name
     * @return
     */
    public boolean isExist(String id, String type, String hostnameIp, int port, String name) {
        FilterGroup filter = new FilterGroup();
        filter.addFilter("dbType", type);
        filter.addFilter("dbHostnameIp", hostnameIp);
        filter.addFilter("dbPort", String.valueOf(port));
        filter.addFilter("dbName", name);
        if (StringUtils.isNotBlank(id)) {
            filter.addFilter("id", FilterGroup.Operator.neq, id);
        }
        List<ConnectMeta> list = this.queryModel(ConnectMeta.class, filter);
        if (list != null && !list.isEmpty()) {
            throw new RuntimeException(String.format("数据库连接已存在：%s://%s:%s/%s", type, hostnameIp, port, name));
        }
        return false;
    }

    /**
     * 批量创建数据库连接
     *
     * @param appId
     * @param connectIds
     * @param userName
     * @param password
     */
    public void batchCreate(String appId, List<String> connectIds, String userName, String password) {
        FilterGroup filter = new FilterGroup();
        filter.addFilter("id", FilterGroup.Operator.in, StringUtils.join(connectIds, ","));
        List<ConnectMeta> connectMetaList = this.queryModel(ConnectMeta.class, filter);
        if (connectMetaList != null && !connectMetaList.isEmpty()) {
            for (ConnectMeta connectMeta : connectMetaList) {
                // 检查数据库连接是否存在
                this.isExist(null, connectMeta.getDbType(), connectMeta.getDbHostnameIp(), connectMeta.getDbPort(), connectMeta.getDbName());
                // 创建数据库连接
                connectMeta.setId(null);
                connectMeta.setDbUserName(userName);
                connectMeta.setDbPassword(password);
                connectMeta.setAppId(appId);
                this.createModel(connectMeta);
            }
        }
    }
}

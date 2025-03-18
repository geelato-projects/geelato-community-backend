package cn.geelato.web.platform.m.model.service;

import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.meta.model.connect.ConnectMeta;
import cn.geelato.core.meta.model.entity.TableCheck;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.meta.model.view.TableView;
import cn.geelato.core.util.ConnectUtils;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.m.base.service.BaseService;
import com.alibaba.fastjson2.JSON;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * @author diabl
 */
@Component
public class DevDbConnectService extends BaseService {

    /**
     * 检查数据库连接是否存在
     * <p>
     * 根据提供的数据库连接元数据信息，检查该连接是否存在。
     *
     * @param meta 数据库连接元数据对象，包含连接所需的ID、数据库类型、数据库主机IP、数据库端口和数据库名称等信息
     * @return 如果数据库连接存在，则返回true；否则返回false
     */
    public boolean isExist(ConnectMeta meta) {
        return this.isExist(meta.getId(), meta.getDbType(), meta.getDbHostnameIp(), meta.getDbPort(), meta.getDbName());
    }

    /**
     * 检查数据库连接是否存在
     * <p>
     * 根据提供的数据库连接信息，检查数据库中是否已存在相同的连接。
     *
     * @param id         连接的唯一标识符
     * @param type       数据库类型
     * @param hostnameIp 数据库主机名或IP地址
     * @param port       数据库端口号
     * @param name       数据库名称
     * @return 如果数据库中已存在相同的连接，则返回true；否则返回false
     * @throws RuntimeException 如果数据库中已存在相同的连接，则抛出运行时异常
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
            String jdbcUrl = ConnectUtils.jdbcUrl(type, hostnameIp, port, name);
            throw new RuntimeException(String.format("数据库连接已存在：%s", jdbcUrl));
        }
        return false;
    }

    /**
     * 批量创建数据库连接
     * <p>
     * 根据提供的应用ID、连接ID列表、用户名和密码，批量创建数据库连接。
     *
     * @param appId      应用ID
     * @param connectIds 连接ID列表
     * @param userName   数据库用户名
     * @param password   数据库密码
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

    public ConnectMeta updateModel(ConnectMeta model) {
        // 源数据
        ConnectMeta source = this.getModel(ConnectMeta.class, model.getId());
        if (source == null) {
            throw new RuntimeException("数据库连接不存在！");
        }
        model = super.updateModel(model);
        if (source.getDbSchema().equals(model.getDbSchema())) {
            return model;
        }
        Map<String, Object> connectMap = JSON.parseObject(JSON.toJSONString(model), Map.class);
        dao.execute("upgradeMetaAfterUpdateSchema", connectMap);
        return model;
    }

    public void isDeleteModel(ConnectMeta model) {
        FilterGroup filter = new FilterGroup();
        filter.addFilter("connectId", model.getId());
        List<TableMeta> tableMetas = queryModel(TableMeta.class, filter);
        if (tableMetas != null && !tableMetas.isEmpty()) {
            throw new RuntimeException("该数据库连接已被关联，无法删除！");
        }
        List<TableView> tableViews = queryModel(TableView.class, filter);
        if (tableViews != null && !tableViews.isEmpty()) {
            throw new RuntimeException("该数据库连接已被关联，无法删除！");
        }
        List<TableCheck> tableChecks = queryModel(TableCheck.class, filter);
        if (tableChecks != null && !tableChecks.isEmpty()) {
            throw new RuntimeException("该数据库连接已被关联，无法删除！");
        }
        super.isDeleteModel(model);
    }
}

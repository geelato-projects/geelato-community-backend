package cn.geelato.archive.engine.channel;

import cn.geelato.archive.exception.ArchiveException;
import cn.geelato.core.ds.DataSourceManager;
import cn.geelato.datasource.DynamicDataSourceRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 归档目标数据源解析：connectId → DataSource。
 * <p>解析顺序：DataSourceManager 注册表（dev_db_connect 体系）→ ORM 动态数据源注册表；
 * connectId 为空表示主库自身（同库归档，返回主数据源）。</p>
 */
@Component
@Slf4j
public class ArchiveTargetDataSourceResolver {

    private final JdbcTemplate primaryJdbcTemplate;
    private final DynamicDataSourceRegistry dynamicRegistry;

    @Autowired
    public ArchiveTargetDataSourceResolver(@Qualifier("primaryDao") cn.geelato.core.orm.Dao primaryDao,
                                           DynamicDataSourceRegistry dynamicRegistry) {
        this.primaryJdbcTemplate = primaryDao.getJdbcTemplate();
        this.dynamicRegistry = dynamicRegistry;
    }

    /** 主库数据源（connectId 空 = 目标是主库自身） */
    public DataSource primary() {
        return primaryJdbcTemplate.getDataSource();
    }

    public boolean isPrimary(String connectId) {
        return connectId == null || connectId.isBlank();
    }

    public DataSource resolve(String connectId) {
        if (isPrimary(connectId)) {
            return primary();
        }
        DataSource ds = DataSourceManager.singleInstance().getRegisteredDataSource(connectId);
        if (ds == null && dynamicRegistry != null) {
            ds = dynamicRegistry.getDataSource(connectId);
        }
        if (ds == null) {
            throw ArchiveException.validationError("归档库连接未注册：" + connectId
                    + "（dev_db_connect / 动态数据源中均未找到，请检查配置后重试）");
        }
        return ds;
    }

    /** 目标库 JdbcTemplate（独立于主库连接池，参数独立调优） */
    public JdbcTemplate targetJdbcTemplate(String connectId) {
        return new JdbcTemplate(resolve(connectId));
    }
}

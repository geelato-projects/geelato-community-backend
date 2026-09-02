package cn.geelato.datasource;

import cn.geelato.web.platform.graal.ApplicationContextProvider;
import org.apache.commons.lang3.StringUtils;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * 平台数据源取用工具。
 *
 * <p>供非 Spring 管理的调用方（Graal 脚本服务、静态工具类等）按 connectId
 * 获取数据源：primary/secondary 取自注册器注入的 Spring 数据源，其余 connectId
 * 由 {@link DynamicDataSourceRegistry} 按配置（platform_dev_db_connect）懒加载建池。
 */
public final class PlatformDataSources {

    private PlatformDataSources() {
    }

    public static DataSource getDataSource(String connectId) {
        DynamicDataSourceRegistry registry = ApplicationContextProvider.getBean(DynamicDataSourceRegistry.class);
        DataSource dataSource = resolve(registry, connectId);
        if (dataSource == null) {
            throw new IllegalStateException("未找到可用的数据源: connectId=" + connectId
                    + "，请确认 platform_dev_db_connect 中存在该连接，且已通过数据源刷新接口加载");
        }
        return dataSource;
    }

    private static DataSource resolve(DynamicDataSourceRegistry registry, String connectId) {
        if ("primary".equalsIgnoreCase(connectId)) {
            return registry.getPrimaryDataSource();
        }
        if ("secondary".equals(connectId)) {
            return registry.getSecondaryDataSource();
        }
        if (StringUtils.isBlank(connectId)) {
            return null;
        }
        return registry.getDataSource(connectId);
    }

    public static DataSource getPrimaryDataSource() {
        return Objects.requireNonNull(ApplicationContextProvider.getBean(DynamicDataSourceRegistry.class).getPrimaryDataSource(),
                "未找到可用的 primary 数据源");
    }
}

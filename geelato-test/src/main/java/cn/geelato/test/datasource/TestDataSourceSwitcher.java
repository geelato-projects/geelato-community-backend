package cn.geelato.test.datasource;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 测试数据源切换器
 * 用于在测试时临时切换到测试数据源
 */
@Slf4j
@Component
public class TestDataSourceSwitcher extends AbstractRoutingDataSource {

    private static final ThreadLocal<String> CONTEXT_HOLDER = new ThreadLocal<>();

    public static final String DEFAULT_DATASOURCE = "defaultDataSource";
    public static final String TEST_DATASOURCE = "testDataSource";

    @Autowired
    public void init(@Qualifier("primaryDataSource") DataSource primaryDataSource,
                     @Qualifier("testDataSource") DataSource testDataSource) {
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DEFAULT_DATASOURCE, primaryDataSource);
        targetDataSources.put(TEST_DATASOURCE, testDataSource);

        super.setTargetDataSources(targetDataSources);
        super.setDefaultTargetDataSource(primaryDataSource);
        super.afterPropertiesSet();
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return getDataSource();
    }

    /**
     * 获取当前数据源
     *
     * @return 当前数据源名称
     */
    public static String getDataSource() {
        String dataSource = CONTEXT_HOLDER.get();
        return dataSource == null ? DEFAULT_DATASOURCE : dataSource;
    }

    /**
     * 设置当前数据源
     *
     * @param dataSource 数据源名称
     */
    public static void setDataSource(String dataSource) {
        log.info("切换数据源: {}", dataSource);
        CONTEXT_HOLDER.set(dataSource);
    }

    /**
     * 清除当前数据源
     */
    public static void clearDataSource() {
        CONTEXT_HOLDER.remove();
    }

    /**
     * 切换到测试数据源
     */
    public static void switchToTestDataSource() {
        setDataSource(TEST_DATASOURCE);
    }

    /**
     * 切换到默认数据源
     */
    public static void switchToDefaultDataSource() {
        setDataSource(DEFAULT_DATASOURCE);
    }
}
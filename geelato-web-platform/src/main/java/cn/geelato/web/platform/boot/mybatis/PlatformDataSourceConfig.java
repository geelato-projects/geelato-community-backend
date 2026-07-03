package cn.geelato.web.platform.boot.mybatis;

import cn.geelato.orm.handler.EntityTableNameHandler;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.apache.ibatis.annotations.Mapper;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;

/**
 * Platform模块MyBatis配置类
 * 参照MarketDataSourceConfig配置MyBatis SqlSessionFactory
 */
@Configuration
@MapperScan(
        basePackages = {"cn.geelato.web.platform.mapper", "cn.geelato.web.platform.srv.weixin.mapper"},
        annotationClass = Mapper.class,
        sqlSessionFactoryRef = "platformSqlSessionFactory"
)
public class PlatformDataSourceConfig {
    private static final String DEBUG_ENV_PATH = "d:\\geelato\\geelato-enterprise\\.dbg\\mybatis-interceptor-error.env";


    /**
     * 配置MyBatis-Plus拦截器
     * @return MybatisPlusInterceptor
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        // #region debug-point A:mp-classpath
        debugReport("A", "[DEBUG] entering mybatisPlusInterceptor", "{\"userDir\":\"" + jsonEscape(System.getProperty("user.dir")) + "\",\"mybatisPlusInterceptor\":\"" + jsonEscape(classSource(MybatisPlusInterceptor.class)) + "\",\"paginationInnerInterceptor\":\"" + jsonEscape(classSource(PaginationInnerInterceptor.class)) + "\",\"selectItem\":\"" + jsonEscape(classSource("net.sf.jsqlparser.statement.select.SelectItem")) + "\"}");
        // #endregion
        try {
            MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

            // 添加动态表名拦截器
            DynamicTableNameInnerInterceptor dynamicTableNameInnerInterceptor = new DynamicTableNameInnerInterceptor();
            dynamicTableNameInnerInterceptor.setTableNameHandler(new EntityTableNameHandler());
            interceptor.addInnerInterceptor(dynamicTableNameInnerInterceptor);
            //分页插件
            interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
            // #region debug-point B:mp-created
            debugReport("B", "[DEBUG] mybatisPlusInterceptor created", "{\"status\":\"success\"}");
            // #endregion
            return interceptor;
        } catch (Throwable ex) {
            // #region debug-point C:mp-failed
            debugReport("C", "[DEBUG] mybatisPlusInterceptor failed", "{\"type\":\"" + jsonEscape(ex.getClass().getName()) + "\",\"message\":\"" + jsonEscape(ex.getMessage()) + "\"}");
            // #endregion
            throw ex;
        }
    }

    /**
     * 配置Platform模块的SqlSessionFactory
     * @param primaryDataSource 主数据源
     * @return SqlSessionFactory
     * @throws Exception 配置异常
     */
    @Bean(name = "platformSqlSessionFactory")
    public SqlSessionFactory platformSqlSessionFactory(
            @Qualifier("primaryDataSource") DataSource primaryDataSource,
            MetaObjectHandler baseEntityMetaObjectHandler) throws Exception {
        MybatisSqlSessionFactoryBean mybatisSqlSessionFactoryBean = new MybatisSqlSessionFactoryBean();
        mybatisSqlSessionFactoryBean.setDataSource(primaryDataSource);
        mybatisSqlSessionFactoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/platform/*Mapper.xml"));
        // 设置拦截器
        mybatisSqlSessionFactoryBean.setPlugins(mybatisPlusInterceptor());

        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setMetaObjectHandler(baseEntityMetaObjectHandler);
        mybatisSqlSessionFactoryBean.setGlobalConfig(globalConfig);
        return mybatisSqlSessionFactoryBean.getObject();
    }

    private static String classSource(Class<?> type) {
        try {
            return type.getName() + "@" + String.valueOf(type.getProtectionDomain().getCodeSource().getLocation());
        } catch (Throwable ex) {
            return type.getName() + "@unknown:" + ex.getClass().getName() + ":" + ex.getMessage();
        }
    }

    private static String classSource(String className) {
        try {
            return classSource(Class.forName(className));
        } catch (Throwable ex) {
            return className + "@missing:" + ex.getClass().getName() + ":" + ex.getMessage();
        }
    }

    private static void debugReport(String hypothesisId, String message, String dataJson) {
        try {
            String debugServerUrl = "http://127.0.0.1:7777/event";
            String sessionId = "mybatis-interceptor-error";
            Path envPath = Path.of(DEBUG_ENV_PATH);
            if (Files.exists(envPath)) {
                for (String line : Files.readAllLines(envPath, StandardCharsets.UTF_8)) {
                    if (line.startsWith("DEBUG_SERVER_URL=")) {
                        debugServerUrl = line.substring("DEBUG_SERVER_URL=".length()).trim();
                    } else if (line.startsWith("DEBUG_SESSION_ID=")) {
                        sessionId = line.substring("DEBUG_SESSION_ID=".length()).trim();
                    }
                }
            }
            String body = "{\"sessionId\":\"" + jsonEscape(sessionId) + "\",\"runId\":\"pre-fix\",\"hypothesisId\":\"" + jsonEscape(hypothesisId) + "\",\"location\":\"PlatformDataSourceConfig.mybatisPlusInterceptor\",\"msg\":\"" + jsonEscape(message) + "\",\"data\":" + dataJson + "}";
            HttpURLConnection connection = (HttpURLConnection) URI.create(debugServerUrl).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
            connection.getInputStream().close();
        } catch (IOException ignored) {
        }
    }

    private static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

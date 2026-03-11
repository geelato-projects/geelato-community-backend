package cn.geelato.mcp.platform.config;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.orm.Dao;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 元数据初始化配置
 * 在应用启动时加载实体的元数据
 */
@Component
public class MetaInitConfig {

    @Autowired(required = false)
    @Qualifier("primaryDao")
    private Dao dao;

    private final MetaManager metaManager = MetaManager.singleInstance();

    @PostConstruct
    public void init() {
        // 初始化类路径中的实体元数据
        String[] packageNames = System.getProperty("geelato.meta.scan-package-names", "cn.geelato").split(",");
        for (String packageName : packageNames) {
            try {
                metaManager.scanAndParse(packageName, false);
            } catch (Exception e) {
                System.out.println("扫描包 " + packageName + " 失败: " + e.getMessage());
            }
        }

        // 如果数据库连接可用，初始化数据库中的实体元数据
        if (dao != null) {
            try {
                metaManager.parseDBMeta(dao);
                System.out.println("数据库元数据初始化完成");
            } catch (Exception e) {
                System.out.println("数据库元数据初始化失败: " + e.getMessage());
            }
        } else {
            System.out.println("数据库连接不可用，跳过数据库元数据初始化");
        }

        System.out.println("MetaManager 初始化完成，实体数量: " + metaManager.getAllEntityNames().size());
    }
}

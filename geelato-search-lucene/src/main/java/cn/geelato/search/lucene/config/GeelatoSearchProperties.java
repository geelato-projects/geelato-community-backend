package cn.geelato.search.lucene.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 搜索模块配置。前缀 {@code geelato.search}。
 *
 * <p>搜索域按业务单据建模（主实体 + 编号字段 + 子实体聚合），可选配置路由实体
 * （如 vt 派生视图实体）提供视图列到域字段的映射，使视图查询同样可路由。
 */
@Setter
@Getter
@ConfigurationProperties(prefix = "geelato.search")
public class GeelatoSearchProperties {

    /** 总开关；false 时不注册监听器与路由器，查询走 SQL REGEXP 等价改写兜底。 */
    private boolean enabled = true;

    /** 索引根目录，每域一个子目录。 */
    private String indexRootDir = "data/search-index";

    /** 单次检索 id 集合上限；命中超过上限放弃路由（回退 SQL），不得截断使用。 */
    private int maxIds = 50000;

    /** reindex 批大小。 */
    private int reindexBatchSize = 500;

    /** 补偿任务 cron（默认每分钟）。 */
    private String compensationCron = "0 * * * * ?";

    /** 对账任务 cron（默认每日凌晨 2 点）。 */
    private String reconcileCron = "0 0 2 * * ?";

    /** 对账批大小。 */
    private int reconcileBatchSize = 500;

    /** 补偿重试退避梯度（分钟）。 */
    private int[] retryBackoffMinutes = {1, 5, 30, 120};

    /** 补偿最大重试次数，超过标记 DEAD（健康告警）。 */
    private int maxRetryCount = 16;

    /** 搜索域配置列表。 */
    private List<DomainConfig> domains = new ArrayList<>();

    @Setter
    @Getter
    public static class DomainConfig {

        /** 域标识（唯一，用作索引目录名）。 */
        private String id;

        /** 主实体名（entityName）。 */
        private String mainEntity;

        /** 主实体主键字段名。 */
        private String pkField = "id";

        /** 主实体编号字段名列表。 */
        private List<String> fields = new ArrayList<>();

        /** 子实体聚合列表。 */
        private List<ChildConfig> children = new ArrayList<>();

        /**
         * 额外可路由实体映射：entityName → 路由配置。
         * mainEntity 自身默认可路由；视图实体（vt 派生表）通过 columnMap
         * 将视图列名映射到域字段名后也可路由。
         */
        private Map<String, RouteEntityConfig> routeEntities = new LinkedHashMap<>();

        @Setter
        @Getter
        public static class ChildConfig {

            /** 子实体名。 */
            private String entity;

            /** 子实体上指向主实体主键的外键字段名。 */
            private String fkField;

            /** 子实体编号字段名列表。 */
            private List<String> fields = new ArrayList<>();
        }

        @Setter
        @Getter
        public static class RouteEntityConfig {

            /** 该实体的主键列名（默认 id；须与域主实体主键同源，如视图暴露的主表 id）。 */
            private String pkColumn = "id";

            /** 视图列名 → 域字段名；该实体下 fuzzymatch 的列不在映射中时不可路由该条件。 */
            private Map<String, String> columnMap = new LinkedHashMap<>();
        }
    }
}

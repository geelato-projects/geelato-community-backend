package cn.geelato.archive.engine.channel;

import cn.geelato.archive.entity.ArchivePolicy;
import cn.geelato.archive.enums.TargetTypeEnum;

import java.util.List;
import java.util.Map;

/**
 * 归档目标通道 SPI：结构核对 / 写入 / 对账 / 在线查询 四接口。
 * <p>一期实现 {@link MySqlArchiveChannel}；二期按同接口扩展 MongoChannel（bulkWrite）、
 * EsChannel（_bulk）与 SeaTunnel 外部执行器。</p>
 */
public interface ArchiveChannel {

    /** 本通道支持的目标类型 */
    TargetTypeEnum supports();

    /**
     * 目标结构核对：目标表/集合/索引不存在则按源表元数据自动创建；
     * 存在但结构漂移 → 抛 70002（绝不静默跳列）。
     *
     * @return 源表列清单（写入 SQL 显式列名用，杜绝 SELECT * 与列序依赖）
     */
    List<String> ensureTargetTable(ArchivePolicy policy, String resolvedTarget);

    /**
     * 阶段1：把本批行写入归档目标（内部含清残留 + 对账复核）。
     *
     * @param sourceColumns ensureTargetTable 返回的源列清单
     * @param ids           本批主键（确定集合，搬移与删除的唯一凭据）
     * @param mode          实际执行模式（SERVER/LOCAL_FILE/JDBC）
     * @param batchDir      LOCAL_FILE 模式的临时目录（{tempDir}/{runId}）
     * @return 实际写入行数（对账失败的异常在内部抛 70003）
     */
    long migrateBatch(ArchivePolicy policy, String resolvedTarget, List<String> sourceColumns,
                      List<String> ids, String mode, java.nio.file.Path batchDir);

    /** 目标侧按主键集合对账计数 */
    long countByIds(String resolvedTarget, List<String> ids);

    /** 归档数据在线只读分页查询（轻量通用：等值过滤 + 分页；经归档库连接查询） */
    Map<String, Object> queryArchived(ArchivePolicy policy, String resolvedTarget, Map<String, Object> filters,
                                      int pageNum, int pageSize);
}

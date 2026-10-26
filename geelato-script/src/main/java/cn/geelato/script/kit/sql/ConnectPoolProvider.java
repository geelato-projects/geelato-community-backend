package cn.geelato.script.kit.sql;

import cn.geelato.core.ds.DataSourceManager;

import javax.sql.DataSource;

/**
 * 按 connectId 取执行用连接池（联邦查询挂载 JdbcSchema 用）。
 * 缺省实现走 core DataSourceManager（primary/默认连接可用）；带 orm Registry 的宿主
 * 应注入覆盖（ScriptEngineBuilder.connectPoolProvider），使动态连接可懒建池。
 */
@FunctionalInterface
public interface ConnectPoolProvider {

    /** 池不可得返回 null，由调用方硬失败（含修复提示）。 */
    DataSource pool(String connectId);

    /** 缺省实现：core 注册表（primary 与已注册池）。 */
    static ConnectPoolProvider defaultProvider() {
        return connectId -> DataSourceManager.singleInstance().getRegisteredDataSource(connectId);
    }
}

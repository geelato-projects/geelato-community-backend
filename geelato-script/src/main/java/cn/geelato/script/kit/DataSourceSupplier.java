package cn.geelato.script.kit;

import javax.sql.DataSource;

/**
 * SQL kit 数据源定位：由消费方装配传入（如按 connect-id 经宿主数据源注册表解析），
 * SDK 不绑死数据源定位机制。
 */
@FunctionalInterface
public interface DataSourceSupplier {

    DataSource get();
}

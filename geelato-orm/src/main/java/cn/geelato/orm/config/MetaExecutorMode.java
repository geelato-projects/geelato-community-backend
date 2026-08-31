package cn.geelato.orm.config;

/**
 * ORM 元数据执行器模式。
 * 当前仅支持 DAO 模式，枚举值预留给后续扩展的执行模式；
 * 新增枚举值时需同步实现 {@code OrmRuntimeProvider#createExecutionStrategy} 的分支，
 * 箭头 switch 表达式会在缺少分支时直接编译失败，避免静默回退。
 */
public enum MetaExecutorMode {
    DAO
}

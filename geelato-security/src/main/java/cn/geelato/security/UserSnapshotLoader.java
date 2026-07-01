package cn.geelato.security;

/**
 * 用户快照加载器。
 * 允许上层替换默认平台表读取实现。
 */
public interface UserSnapshotLoader {

    UserSnapshot load(UserOrgInfoEnricher userOrgInfoEnricher);
}

package cn.geelato.security;

/**
 * 角色快照加载器。
 * 允许上层替换默认平台表读取实现。
 */
public interface RoleSnapshotLoader {

    RoleSnapshot load();
}

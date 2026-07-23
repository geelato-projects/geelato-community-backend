package cn.geelato.ide.service;

/**
 * IDE 脚本乐观锁冲突异常。
 * <p>
 * 触发场景：客户端读到的 version 与服务端不一致（被其他用户/进程修改过）。
 *
 * @author geelato
 */
public class IdeOptimisticLockException extends RuntimeException {

    private final Integer serverVersion;
    private final Integer clientVersion;

    public IdeOptimisticLockException(Integer serverVersion, Integer clientVersion) {
        super(String.format("脚本版本冲突：服务端 version=%s，客户端 version=%s，请先 pull 最新版本。",
                serverVersion, clientVersion));
        this.serverVersion = serverVersion;
        this.clientVersion = clientVersion;
    }

    public Integer getServerVersion() {
        return serverVersion;
    }

    public Integer getClientVersion() {
        return clientVersion;
    }
}

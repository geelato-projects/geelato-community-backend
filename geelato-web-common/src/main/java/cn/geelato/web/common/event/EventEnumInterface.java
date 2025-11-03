package cn.geelato.web.common.event;

public interface EventEnumInterface<T> {
    // 获取事件唯一标识
    String getEventCode();

    // 事件处理逻辑（参数为业务数据）
    void handle(T data);
}

package cn.geelato.alert.engine.deliver;

import java.util.List;
import java.util.Map;

/**
 * geelato-message 出站传输 SPI：enqueue 投递 + bizKey 回查。引擎依赖本接口而非 HTTP 实现，单测注入 fake。
 */
public interface MessageTransport {

    /**
     * POST /message/enqueue：一次一条消息；message 侧以 bizKey 幂等（重复投递返回原 id 不重复入队）。
     *
     * @return platform_msg id 清单（现行契约恒单元素）
     * @throws DeliverException 网络/非 2xx/响应解析失败
     */
    List<String> enqueue(EnqueuePayload payload) throws DeliverException;

    /**
     * GET /message/ops/messages?bizKey=...：按 bizKey 回查投递明细（渠道/接收人/状态/重试/死信原因）。
     *
     * @return message 行明细（platform_msg 列的 Map 视图）
     * @throws DeliverException 网络/非 2xx/响应解析失败
     */
    List<Map<String, Object>> listByBizKey(String bizKey) throws DeliverException;
}

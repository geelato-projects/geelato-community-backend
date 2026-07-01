package cn.geelato.web.platform.sse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SseMessage {
    private String topic;
    private Object data;
}

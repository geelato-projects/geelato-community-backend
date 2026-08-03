package cn.geelato.web.platform.srv.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 投递渠道执行结果。
 * success=true 表示整批投递成功（可 mark success）；false 表示失败，由 outbox 调度器决定重试或死信。
 * failedRecipients 记录部分失败的收件人，便于排查（站内信场景下通常全部成功或全部失败）。
 *
 * @author geelato
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChannelResult {

    private boolean success;
    private String errorMessage;
    private List<String> failedRecipients = new ArrayList<>();

    public static ChannelResult ok() {
        return new ChannelResult(true, null, new ArrayList<>());
    }

    public static ChannelResult fail(String errorMessage) {
        return new ChannelResult(false, errorMessage, new ArrayList<>());
    }
}

package cn.geelato.web.platform.srv.announcement;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 公告 git 直连配置。
 * <p>公告来源为 git 仓库（https 协议）：私有仓库配 username + password（个人访问令牌 PAT），
 * 公开仓库两者留空走匿名。本地工作目录见 {@code geelato.announcement.dir}。</p>
 *
 * @author geelato
 */
@Data
@Component
@ConfigurationProperties(prefix = "geelato.announcement.git")
public class AnnouncementGitProperties {

    /** 仓库地址（https）；必配，为空时同步直接报错 */
    private String repoUri = "";

    /** 分支名 */
    private String branch = "main";

    /** 私有仓库访问用户名（公开仓库留空） */
    private String username = "";

    /** 私有仓库访问令牌 PAT（公开仓库留空），建议环境变量注入，不落配置文件明文 */
    private String password = "";

    /** 是否已配置仓库地址 */
    public boolean enabled() {
        return repoUri != null && !repoUri.isBlank();
    }
}

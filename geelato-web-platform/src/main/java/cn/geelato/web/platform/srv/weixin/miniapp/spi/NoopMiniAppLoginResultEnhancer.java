package cn.geelato.web.platform.srv.weixin.miniapp.spi;

import cn.geelato.web.platform.srv.weixin.miniapp.entity.MiniAppLoginResult;
import cn.geelato.meta.User;

/**
 * 空实现：宿主未提供增强器时的兜底，不做任何填充。
 */
public class NoopMiniAppLoginResultEnhancer implements MiniAppLoginResultEnhancer {

    @Override
    public void enhance(MiniAppLoginResult result, User user) {
        // 平台默认：不填充业务字段（roleCodes/cooperatingOrgName 等由宿主实现的增强器负责）
    }
}

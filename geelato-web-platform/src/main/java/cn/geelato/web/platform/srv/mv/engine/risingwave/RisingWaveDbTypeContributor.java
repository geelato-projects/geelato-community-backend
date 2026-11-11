package cn.geelato.web.platform.srv.mv.engine.risingwave;

import cn.geelato.core.enums.Dialects;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * RisingWave 的 JDBC 方言声明：risingwave 注册为 postgresql 方言家族（PG 协议）。
 * <p>框架层（orm/core）不感知具体引擎；引擎适配器自带方言家族注册，
 * 注册后驱动、URL 模板、建池与测试连接按家族方言生效。
 */
@Component
public class RisingWaveDbTypeContributor {

    static final String DB_TYPE = "risingwave";

    @PostConstruct
    public void contribute() {
        Dialects.registerAlias(DB_TYPE, Dialects.POSTGRESQL);
    }
}

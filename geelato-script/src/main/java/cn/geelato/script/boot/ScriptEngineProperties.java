package cn.geelato.script.boot;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "geelato.script.engine")
public class ScriptEngineProperties {

    /** 单执行语句数上限（防死循环）。 */
    private long statementLimit = 1_000_000L;
    /** 执行 worker 线程池大小。 */
    private int workerPoolSize = 4;
    /** python 语言支持（需 GraalVM 运行时，仅执行 python 脚本时要求语言组件在位）。 */
    private boolean pythonEnabled = true;
    /** {@code @GraalService} 类路径扫描包（SDK 不内置 kit，消费方 kit 所在包在此追加，如 cn.geelato.script.kit.demo 可扫到 DemoService；缺省不扫描）。 */
    private List<String> scanPackages = new ArrayList<>();
    /** 共享 Engine 复用编译缓存（关闭则每 Context 独立 Engine）。 */
    private boolean engineShared = true;
    /** 缺省引擎版本。 */
    private String defaultVersion = "v1";
    /** js.ecmascript-version（v1 基线）。 */
    private String defaultEsVersion = "2022";

    private final Sql sql = new Sql();
    private final Http http = new Http();
    private final Dao dao = new Dao();
    private final Encrypt encrypt = new Encrypt();

    @Getter
    @Setter
    public static class Sql {
        /** 单查询行上限（超限截断）。 */
        private int rowLimit = 10_000;
        /** 单查询超时毫秒。 */
        private int queryTimeoutMs = 10_000;
    }

    @Getter
    @Setter
    public static class Http {
        /** 出网总开关；空白名单时仍拒绝所有请求。 */
        private boolean enabled = true;
        private List<String> allowlist = new ArrayList<>();
        private int timeoutMs = 5_000;
    }

    @Getter
    @Setter
    public static class Dao {
        /** ORM 写通道总开关（save/insert/update/remove/batchSave 与 mql 写方法）；缺省 dry-run 回滚。 */
        private boolean writeEnabled = true;
        /** 写通道缺省 dry-run（true=finally 永远回滚）。 */
        private boolean dryRun = true;
    }

    @Getter
    @Setter
    public static class Encrypt {
        /** AES 密钥（$gl.encrypt.aes/aesDecrypt；空则两方法抛错）。 */
        private String aesKey = "";
    }
}

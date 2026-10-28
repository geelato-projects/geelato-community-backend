package cn.geelato.alert.engine;

/** 引擎线程（调度/事件监听）无用户会话，审计位与操作者统一用系统身份。 */
public final class EngineConsts {

    private EngineConsts() {
    }

    public static final String SYSTEM_USER_ID = "system";
    public static final String SYSTEM_USER_NAME = "告警引擎";

    public static String nowBatchNo(String prefix) {
        return prefix + "-" + System.currentTimeMillis();
    }
}

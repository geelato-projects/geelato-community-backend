package cn.geelato.core.experiment;

/**
 * 实验功能开关判定（平台实验功能规范的权威定义）。
 *
 * <h2>规范</h2>
 * <ol>
 *   <li><b>构建期白名单</b>：实验特性是否允许开启在打包成 jar 时即已决定——
 *       白名单是 {@link ExperimentFeatures#allowed()} 代码常量，运行时不可更改
 *       （application.properties / 配置中心均不提供开关）。</li>
 *   <li><b>唯一运行时开启通道</b>：HTTP header {@code X-Gl-Experiments: feature-a,feature-b}
 *       （逗号分隔多值，容忍空白，功能名大小写不敏感；命名跟随平台 X-Gl-* 惯例），
 *       由 Web 层 ExperimentFilter 解析进 {@link ExperimentContext}。白名单外的
 *       功能名静默忽略（记 debug 日志）。</li>
 *   <li><b>合并语义</b>：{@code isEnabled = 构建期白名单包含 && 请求已声明开启}。
 *       请求级只能增开、不能关闭；无请求上下文（定时任务、异步事件线程）恒为关
 *       ——实验功能默认关。</li>
 *   <li><b>生效点约定</b>：读路径（查询/解析/路由等）以 {@link #isEnabled(String)} 门控；
 *       数据准备路径（索引同步、补偿、缓存预热等）不门控，保持运行，保证任意时刻
 *       开启即可用。</li>
 *   <li><b>分支点标记</b>：每个实验分支点（调用 {@code isEnabled} 门控的位置）必须
 *       在判定行上方携带统一格式的标记注释：
 *       <pre>{@code // [experiment:search] 实验分支点 —— 毕业时删除本判定与关闭分支，仅保留开启路径}</pre>
 *       标记格式 {@code [experiment:<功能名>]}，全库可 grep（{@code grep "\[experiment:"}
 *       列出全部分支点，按功能名过滤逐个清理），保证特性转正时可完整移除实验残留。</li>
 *   <li><b>毕业</b>：特性验证稳定后移除生效点判定与常量登记，成为正式行为——
 *       按分支点标记逐处删除判定与关闭分支，再清空 {@link ExperimentFeatures#allowed()}
 *       中的登记与常量，最后全库 grep 标记确认为零。</li>
 * </ol>
 *
 * <h2>接入示例</h2>
 * <pre>{@code
 * if (!ExperimentGate.isEnabled(ExperimentFeatures.SEARCH)) {
 *     return false; // 回到特性开启前的原路径
 * }
 * }</pre>
 */
public final class ExperimentGate {

    private ExperimentGate() {
    }

    /**
     * 判定实验功能当前是否生效：构建期白名单包含 && 本请求已声明开启。
     */
    public static boolean isEnabled(String feature) {
        if (feature == null || feature.isBlank()) {
            return false;
        }
        return ExperimentFeatures.allowed().contains(feature) && ExperimentContext.isEnabled(feature);
    }
}

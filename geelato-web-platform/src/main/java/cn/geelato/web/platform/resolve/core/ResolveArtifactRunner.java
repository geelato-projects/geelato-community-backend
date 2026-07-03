package cn.geelato.web.platform.resolve.core;

import cn.geelato.web.platform.resolve.model.ResolveStepResult;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ResolveArtifactRunner {
    private ResolveArtifactRunner() {
    }

    /**
     * 统一执行单个 artifact，并记录步骤结果、耗时和错误摘要。
     */
    public static void run(ResolveArtifact artifact, ResolveContext ctx) {
        ResolveStepResult step = new ResolveStepResult();
        step.setArtifactId(artifact.getId());
        long start = System.currentTimeMillis();
        try {
            if (log.isDebugEnabled()) {
                log.debug("Running resolve artifact: artifactId={}, biztag={}, sourceFileName={}",
                        artifact.getId(),
                        ctx == null ? null : ctx.getBiztag(),
                        ctx == null ? null : ctx.getSourceFileName());
            }
            if (!artifact.supports(ctx)) {
                step.setSuccess(true);
                step.setOutput("skipped");
            } else {
                Object output = artifact.execute(ctx);
                step.setSuccess(true);
                step.setOutput(output);
            }
        } catch (Exception e) {
            step.setSuccess(false);
            step.setErrorMsg(e.getMessage());
            log.debug("Resolve artifact failed: artifactId={}, error={}", artifact.getId(), e.getMessage());
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        } finally {
            step.setCostMs(System.currentTimeMillis() - start);
            ctx.getSteps().add(step);
            if (log.isDebugEnabled()) {
                log.debug("Resolve artifact finished: artifactId={}, success={}, costMs={}",
                        artifact.getId(),
                        step.getSuccess(),
                        step.getCostMs());
            }
        }
    }
}

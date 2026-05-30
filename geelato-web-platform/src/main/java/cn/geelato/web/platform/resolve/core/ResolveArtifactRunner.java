package cn.geelato.web.platform.resolve.core;

import cn.geelato.web.platform.resolve.model.ResolveStepResult;

public class ResolveArtifactRunner {
    private ResolveArtifactRunner() {
    }

    public static void run(ResolveArtifact artifact, ResolveContext ctx) {
        ResolveStepResult step = new ResolveStepResult();
        step.setArtifactId(artifact.getId());
        long start = System.currentTimeMillis();
        try {
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
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        } finally {
            step.setCostMs(System.currentTimeMillis() - start);
            ctx.getSteps().add(step);
        }
    }
}

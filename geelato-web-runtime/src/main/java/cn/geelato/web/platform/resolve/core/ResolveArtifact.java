package cn.geelato.web.platform.resolve.core;

public interface ResolveArtifact {
    String getId();

    boolean supports(ResolveContext ctx);

    Object execute(ResolveContext ctx) throws Exception;
}


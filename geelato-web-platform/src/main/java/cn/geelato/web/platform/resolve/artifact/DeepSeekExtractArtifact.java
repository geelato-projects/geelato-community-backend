package cn.geelato.web.platform.resolve.artifact;

import cn.geelato.web.platform.resolve.client.DeepSeekClient;
import cn.geelato.web.platform.resolve.core.ResolveArtifact;
import cn.geelato.web.platform.resolve.core.ResolveContext;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

@Component
public class DeepSeekExtractArtifact implements ResolveArtifact {
    private final DeepSeekClient deepSeekClient;

    public DeepSeekExtractArtifact(DeepSeekClient deepSeekClient) {
        this.deepSeekClient = deepSeekClient;
    }

    @Override
    public String getId() {
        return "ai.extract";
    }

    @Override
    public boolean supports(ResolveContext ctx) {
        Object fullMd = ctx == null ? null : ctx.getArtifactData("mineru.fullMd");
        return fullMd != null && Strings.isNotBlank(fullMd.toString());
    }

    @Override
    public Object execute(ResolveContext ctx) throws Exception {
        String fullMd = ctx.getArtifactData("mineru.fullMd").toString();
        JSONObject params = ctx.getParams();
        String prompt = params == null ? null : params.getString("prompt");
        String aiText = deepSeekClient.extract(prompt, fullMd);

        Object parsed = aiText;
        if (Strings.isNotBlank(aiText)) {
            try {
                parsed = JSON.parse(aiText);
            } catch (Exception ignored) {
            }
        }

        ctx.putArtifactData("ai.response", parsed);
        ctx.setResult(parsed);
        return parsed;
    }
}

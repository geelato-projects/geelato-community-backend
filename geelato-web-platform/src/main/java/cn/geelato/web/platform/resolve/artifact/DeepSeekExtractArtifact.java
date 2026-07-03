package cn.geelato.web.platform.resolve.artifact;

import cn.geelato.web.platform.resolve.client.DeepSeekClient;
import cn.geelato.web.platform.resolve.core.ResolveArtifact;
import cn.geelato.web.platform.resolve.core.ResolveContext;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

@Component
@Slf4j
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

    /**
     * 调用 DeepSeek 对 markdown 内容做结构化抽取，并尽量将返回结果解析为 JSON 对象。
     */
    @Override
    public Object execute(ResolveContext ctx) throws Exception {
        String fullMd = ctx.getArtifactData("mineru.fullMd").toString();
        JSONObject params = ctx.getParams();
        String prompt = params == null ? null : params.getString("prompt");
        if (log.isDebugEnabled()) {
            log.debug("Submitting AI extraction: biztag={}, sourceFileName={}, markdownLength={}, promptLength={}",
                    ctx == null ? null : ctx.getBiztag(),
                    ctx == null ? null : ctx.getSourceFileName(),
                    fullMd == null ? 0 : fullMd.length(),
                    prompt == null ? 0 : prompt.length());
        }
        String aiText = deepSeekClient.extract(prompt, fullMd);

        Object parsed = aiText;
        if (Strings.isNotBlank(aiText)) {
            try {
                parsed = JSON.parse(aiText);
            } catch (Exception ignored) {
                log.debug("AI extraction response is not valid JSON: biztag={}, responseLength={}",
                        ctx == null ? null : ctx.getBiztag(),
                        aiText.length());
            }
        }

        ctx.putArtifactData("ai.response", parsed);
        ctx.setResult(parsed);
        if (log.isDebugEnabled()) {
            log.debug("AI extraction finished: biztag={}, parsedType={}, responseLength={}",
                    ctx == null ? null : ctx.getBiztag(),
                    parsed == null ? null : parsed.getClass().getSimpleName(),
                    aiText == null ? 0 : aiText.length());
        }
        return parsed;
    }
}

package cn.geelato.web.platform.srv.report.service;

import cn.geelato.web.platform.resolve.client.DeepSeekClient;
import cn.geelato.web.platform.srv.report.dto.TemplateAdjustRequest;
import cn.geelato.web.platform.srv.report.dto.TemplateAdjustResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class TemplateAdjustService {
    private final DeepSeekClient deepSeekClient;

    public TemplateAdjustResult adjustTemplate(TemplateAdjustRequest request) throws IOException {
        validateRequest(request);
        try {
            String templateContent = deepSeekClient.chat(buildPrompt(request), buildSystemPrompt());
            if (templateContent == null || templateContent.isBlank()) {
                throw new IllegalStateException("DeepSeek returned empty template content");
            }
            return new TemplateAdjustResult(stripMarkdownFence(templateContent.trim()));
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException || e instanceof IllegalStateException) {
                throw e;
            }
            throw new IllegalStateException("DeepSeek request failed", e);
        }
    }

    private void validateRequest(TemplateAdjustRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (request.getTemplateContent() == null || request.getTemplateContent().isBlank()) {
            throw new IllegalArgumentException("templateContent is required");
        }
        if (request.getFeedback() == null || request.getFeedback().isBlank()) {
            throw new IllegalArgumentException("feedback is required");
        }
    }

    private String buildSystemPrompt() {
        return "你是一个报告模板调整助手，只能基于给定的模板内容和数据集定义进行模板修改。";
    }

    private String buildPrompt(TemplateAdjustRequest request) {
        return "请按照" + request.getFeedback()
                + "，将" + request.getTemplateContent()
                + "的内容进行修改，请严格按照可选的数据集" + blankToDefault(request.getTemplateSchema(), "{}")
                + "进行匹配，仅能对模板中的数据集部分进行隐藏和显示，然后重新调整页面样式，返回新的template_content。";
    }

    private String stripMarkdownFence(String content) {
        if (content.startsWith("```")) {
            int firstLineEnd = content.indexOf('\n');
            if (firstLineEnd > -1) {
                content = content.substring(firstLineEnd + 1);
            }
            if (content.endsWith("```")) {
                content = content.substring(0, content.length() - 3);
            }
        }
        return content.trim();
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}

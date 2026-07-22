package cn.geelato.web.platform.srv.report.service;

import cn.geelato.web.platform.resolve.client.DeepSeekClient;
import cn.geelato.web.platform.srv.report.dto.TemplateAdjustRequest;
import cn.geelato.web.platform.srv.report.dto.TemplateAdjustResult;
import cn.geelato.web.platform.srv.report.dto.TemplateReflowRequest;
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

    public TemplateAdjustResult reflowTemplate(TemplateReflowRequest request) throws IOException {
        validateReflowRequest(request);
        try {
            String templateContent = deepSeekClient.chat(buildReflowPrompt(request), buildSystemPrompt());
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

    private void validateReflowRequest(TemplateReflowRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (request.getTemplateContent() == null || request.getTemplateContent().isBlank()) {
            throw new IllegalArgumentException("templateContent is required");
        }
    }

    private String buildSystemPrompt() {
        return "你是一个报告模板调整助手，只能基于给定的模板内容和数据集定义进行模板修改。返回结果必须是完整、合法、可直接渲染的 HTML 文本，不得包含 Markdown 代码围栏、注释说明或任何非 HTML 内容。";
    }

    private String buildReflowPrompt(TemplateReflowRequest request) {
        return "给定以下报告模板内容：" + request.getTemplateContent()
                + "；以及可配置数据集（JSON 数组，每项包含 path、label、visible 等字段）：" + blankToDefault(request.getTemplateSchema(), "[]")
                + "。请移除或隐藏 visible 为 false 的字段所绑定的区域，保留 visible 为 true 的字段，并重新排布页面布局与样式，消除因隐藏字段产生的空白与样式错位，保持整体风格协调美观。输出必须是完整、合法、可直接渲染的 HTML 文本（保留原模板中的占位符与 HTML 结构），仅返回完整的新的 template_content，不要输出 Markdown 代码围栏或任何解释说明。";
    }

    private String buildPrompt(TemplateAdjustRequest request) {
        return "请按照" + request.getFeedback()
                + "，将" + request.getTemplateContent()
                + "的内容进行修改，请严格按照可选的数据集" + blankToDefault(request.getTemplateSchema(), "{}")
                + "进行匹配，仅能对模板中的数据集部分进行隐藏和显示，然后重新调整页面样式。输出必须是完整、合法、可直接渲染的 HTML 文本（保留原模板中的占位符与 HTML 结构），仅返回新的 template_content，不要输出 Markdown 代码围栏或任何解释说明。";
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

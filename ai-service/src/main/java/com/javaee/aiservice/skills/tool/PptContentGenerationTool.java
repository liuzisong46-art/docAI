package com.javaee.aiservice.skills.tool;

import com.javaee.aiservice.agent.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** Uses the chat model to turn an outline into structured slides. */
public class PptContentGenerationTool implements SkillTool<PptContentGenerationTool.Input, List<PptSlide>> {

    private static final Logger log = LoggerFactory.getLogger(PptContentGenerationTool.class);
    private final ChatService chatService;

    public PptContentGenerationTool(ChatService chatService) {
        this.chatService = chatService;
    }

    @Override
    public String id() {
        return "ppt-content-generate";
    }

    @Override
    public List<PptSlide> execute(Input input) {
        List<PptSlide> slides = new ArrayList<>();
        slides.add(new PptSlide("cover", input.title(), "", "", coverNotes(input.title())));

        try {
            String prompt = """
                    请根据以下大纲生成一个 HTML PPT 演示文稿的内容结构，包括每一页的内容和演讲者逐字稿。

                    要求：
                    1. 每页有一个标题
                    2. 每页有 2-4 个要点
                    3. 每页要有 150-300 字的逐字稿
                    4. 每页一行：页面标题 | 要点1 | 要点2 | 要点3 | 逐字稿内容
                    5. 只返回内容，不要其他说明

                    大纲：
                    %s
                    """.formatted(input.outline());
            String response = input.model() == null || input.model().isBlank()
                    ? chatService.callChatApi(prompt)
                    : chatService.callChatApiWithModelCode(prompt, input.model());
            parseModelResponse(response, slides);
        } catch (Exception e) {
            log.warn("PPT内容生成工具调用模型失败，使用大纲降级生成: {}", e.getMessage());
        }

        if (slides.size() <= 1) {
            slides = parseOutline(input.outline(), input.title());
        }
        slides.add(new PptSlide("thanks", "谢谢观看", "Thank you for watching", "", thanksNotes()));
        return slides;
    }

    private void parseModelResponse(String response, List<PptSlide> slides) {
        if (response == null) {
            return;
        }
        for (String rawLine : response.split("\\n")) {
            String line = rawLine.trim();
            if (!line.contains("|")) {
                continue;
            }
            String[] parts = line.split("\\|");
            if (parts.length < 2) {
                continue;
            }
            StringBuilder content = new StringBuilder("<ul>");
            String notes = "";
            for (int i = 1; i < parts.length; i++) {
                String part = parts[i].trim();
                if (part.isEmpty()) {
                    continue;
                }
                if (i == parts.length - 1 && part.length() > 50) {
                    notes = part;
                } else {
                    content.append("<li>").append(escapeHtml(part)).append("</li>");
                }
            }
            content.append("</ul>");
            String title = parts[0].trim();
            if (notes.isBlank()) {
                notes = "这一页主要讲 " + title + "，接下来详细介绍...";
            }
            slides.add(new PptSlide("content", title, "", content.toString(), notes));
        }
    }

    private List<PptSlide> parseOutline(String outline, String title) {
        List<PptSlide> slides = new ArrayList<>();
        slides.add(new PptSlide("cover", title, "", "", coverNotes(title)));
        String normalized = outline == null ? "" : outline.replace("\\n", "\n");
        List<String> items = normalized.lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
        for (int i = 0; i < items.size(); i += 3) {
            StringBuilder content = new StringBuilder("<ul>");
            for (int j = i; j < i + 3 && j < items.size(); j++) {
                String item = items.get(j)
                        .replaceAll("^[-*•]\\s+", "")
                        .replaceAll("^\\d+[.、)）]\\s*", "")
                        .replaceAll("^Slide\\s*\\d*[:：]\\s*", "");
                content.append("<li>").append(escapeHtml(item)).append("</li>");
            }
            content.append("</ul>");
            slides.add(new PptSlide("content", items.get(i), "", content.toString(),
                    "这一页主要讲 " + items.get(i) + "，接下来详细介绍..."));
        }
        return slides;
    }

    private String coverNotes(String title) {
        return "<p>大家好，欢迎来到今天的分享。今天我们来讲讲<strong>" + escapeHtml(title)
                + "</strong>。</p><p>希望今天的内容能给大家一些启发，有问题随时提问。</p>";
    }

    private String thanksNotes() {
        return "<p>以上就是今天分享的全部内容了。</p><p>现在是问答环节，谢谢大家！</p>";
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#039;");
    }

    public record Input(String outline, String title, String model) {
    }
}

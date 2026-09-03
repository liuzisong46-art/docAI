package com.javaee.aiservice.skills.tool;

import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Renders structured slides and bundled assets into one self-contained HTML document. */
public class HtmlPresentationRenderTool implements SkillTool<HtmlPresentationRenderTool.Input, String> {

    @Override
    public String id() {
        return "html-presentation-render";
    }

    @Override
    public String execute(Input input) {
        String fontsCss = readResource("static/ppt-templates/assets/fonts.css");
        String baseCss = readResource("static/ppt-templates/assets/base.css");
        String themeCss = readResource("static/ppt-templates/assets/themes/" + input.theme() + ".css");
        String animationsCss = readResource("static/ppt-templates/assets/animations/animations.css");
        String runtimeJs = readResource("static/ppt-templates/assets/runtime.js");
        String fxRuntimeJs = readResource("static/ppt-templates/assets/animations/fx-runtime.js");

        StringBuilder html = new StringBuilder("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n<meta charset=\"utf-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n")
                .append("<title>").append(escapeHtml(input.title())).append("</title>\n");
        appendStyle(html, fontsCss);
        appendStyle(html, baseCss);
        appendStyle(html, themeCss);
        appendStyle(html, animationsCss);
        html.append("</head>\n<body>\n<div class=\"deck\">\n");

        for (int i = 0; i < input.slides().size(); i++) {
            PptSlide slide = input.slides().get(i);
            html.append("<section class=\"slide\" data-title=\"").append(escapeHtml(slide.title())).append("\">\n");
            if ("cover".equals(slide.type()) || "thanks".equals(slide.type())) {
                html.append("<h1 class=\"h1 anim-fade-up\" data-anim=\"fade-up\">")
                        .append(escapeHtml(slide.title())).append("</h1>\n");
            } else {
                html.append("<h2 class=\"h2 anim-fade-up\" data-anim=\"fade-up\">")
                        .append(escapeHtml(slide.title())).append("</h2>\n")
                        .append("<div class=\"stack mt-l\"><div class=\"content\">")
                        .append(cleanHtml(slide.content())).append("</div></div>\n");
            }
            html.append("<div class=\"deck-footer\"><span class=\"slide-number\" data-current=\"")
                    .append(i + 1).append("\" data-total=\"").append(input.slides().size()).append("\"></span></div>\n")
                    .append("<aside class=\"notes\">").append(cleanHtml(slide.notes())).append("</aside>\n")
                    .append("</section>\n");
        }
        html.append("</div>\n");
        appendScript(html, fxRuntimeJs);
        appendScript(html, runtimeJs);
        return html.append("</body>\n</html>").toString();
    }

    private String readResource(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append('\n');
                }
            }
            return content.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private void appendStyle(StringBuilder html, String css) {
        if (!css.isEmpty()) html.append("<style>\n").append(css).append("</style>\n");
    }

    private void appendScript(StringBuilder html, String script) {
        if (!script.isEmpty()) html.append("<script>\n").append(script).append("</script>\n");
    }

    private String cleanHtml(String html) {
        return html == null ? "" : html.trim().replaceAll("\\s+", " ").replaceAll(">\\s+<", "><");
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#039;");
    }

    public record Input(List<PptSlide> slides, String theme, String title) {
    }
}

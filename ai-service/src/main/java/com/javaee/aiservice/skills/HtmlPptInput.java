package com.javaee.aiservice.skills;

/** Input contract for HTML PPT generation. */
public record HtmlPptInput(
        String outline,
        String theme,
        String title,
        String model
) {
    public HtmlPptInput {
        theme = theme == null || theme.isBlank() ? "tokyo-night" : theme;
        title = title == null || title.isBlank() ? "演示文稿" : title;
    }
}

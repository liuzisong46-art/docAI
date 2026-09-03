package com.javaee.aiservice.skills;

import java.util.LinkedHashMap;
import java.util.Map;

/** Output contract for HTML PPT generation. */
public record HtmlPptOutput(
        String status,
        String fileName,
        String filePath,
        String title,
        String theme,
        String model,
        int slideCount,
        String htmlContent
) {
    /** Compatibility view for the existing REST endpoints. */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("fileName", fileName);
        result.put("filePath", filePath);
        result.put("title", title);
        result.put("theme", theme);
        result.put("model", model);
        result.put("slideCount", slideCount);
        result.put("htmlContent", htmlContent);
        return result;
    }
}

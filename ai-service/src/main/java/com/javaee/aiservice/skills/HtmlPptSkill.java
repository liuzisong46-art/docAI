package com.javaee.aiservice.skills;

import com.javaee.aiservice.skills.tool.HtmlPresentationRenderTool;
import com.javaee.aiservice.skills.tool.LocalArtifactWriteTool;
import com.javaee.aiservice.skills.tool.PptContentGenerationTool;
import com.javaee.aiservice.skills.tool.PptSlide;

import java.util.List;
import java.util.Map;

/**
 * Composite workflow: generate slide content -> render HTML -> persist artifact.
 * The individual steps are atomic tools; this class owns their order and data flow.
 */
public class HtmlPptSkill implements TypedSkill<HtmlPptInput, HtmlPptOutput> {

    public static final SkillDefinition DEFINITION = new SkillDefinition(
            "html-ppt-generate",
            "HTML PPT Skill",
            "根据大纲编排内容生成、HTML渲染和产物保存，输出可演示的HTML PPT",
            Map.of(
                    "outline", "PPT大纲",
                    "theme", "主题，默认tokyo-night",
                    "title", "标题",
                    "model", "可选模型代码"
            ),
            java.util.Set.of("outline"),
            false,
            "presentation",
            false
    );

    private final PptContentGenerationTool contentGenerationTool;
    private final HtmlPresentationRenderTool renderTool;
    private final LocalArtifactWriteTool artifactWriteTool;

    public HtmlPptSkill(PptContentGenerationTool contentGenerationTool,
                        HtmlPresentationRenderTool renderTool,
                        LocalArtifactWriteTool artifactWriteTool) {
        this.contentGenerationTool = contentGenerationTool;
        this.renderTool = renderTool;
        this.artifactWriteTool = artifactWriteTool;
    }

    @Override
    public String getName() {
        return DEFINITION.legacyName();
    }

    @Override
    public String getDescription() {
        return DEFINITION.description();
    }

    @Override
    public SkillDefinition getDefinition() {
        return DEFINITION;
    }

    @Override
    public Class<HtmlPptInput> getInputType() {
        return HtmlPptInput.class;
    }

    @Override
    public Object execute(Object... parameters) {
        String outline = parameters.length > 0 && parameters[0] != null ? (String) parameters[0] : "";
        String theme = parameters.length > 1 && parameters[1] != null ? (String) parameters[1] : "tokyo-night";
        String title = parameters.length > 2 && parameters[2] != null ? (String) parameters[2] : "演示文稿";
        String model = parameters.length > 3 && parameters[3] != null ? (String) parameters[3] : null;
        return executeTyped(new HtmlPptInput(outline, theme, title, model)).toMap();
    }

    @Override
    public HtmlPptOutput executeTyped(HtmlPptInput input) {
        if (input == null || input.outline() == null || input.outline().isBlank()) {
            throw new IllegalArgumentException("PPT大纲不能为空");
        }

        List<PptSlide> slides = contentGenerationTool.execute(
                new PptContentGenerationTool.Input(input.outline(), input.title(), input.model()));
        String html = renderTool.execute(
                new HtmlPresentationRenderTool.Input(slides, input.theme(), input.title()));
        LocalArtifactWriteTool.Output artifact = artifactWriteTool.execute(
                new LocalArtifactWriteTool.Input("ppt", ".html", html));

        return new HtmlPptOutput("success", artifact.fileName(), artifact.filePath(), input.title(),
                input.theme(), input.model(), slides.size(), html);
    }
}

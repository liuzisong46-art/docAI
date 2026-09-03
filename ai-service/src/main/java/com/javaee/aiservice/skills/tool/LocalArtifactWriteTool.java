package com.javaee.aiservice.skills.tool;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Persists generated content as a local temporary artifact. */
public class LocalArtifactWriteTool implements SkillTool<LocalArtifactWriteTool.Input, LocalArtifactWriteTool.Output> {

    @Override
    public String id() {
        return "local-artifact-write";
    }

    @Override
    public Output execute(Input input) {
        try {
            String fileName = input.prefix() + "-" + UUID.randomUUID() + input.extension();
            Path path = Path.of(System.getProperty("java.io.tmpdir"), fileName);
            Files.writeString(path, input.content(), StandardCharsets.UTF_8);
            return new Output(fileName, path.toString());
        } catch (Exception e) {
            throw new RuntimeException("写入生成文件失败: " + e.getMessage(), e);
        }
    }

    public record Input(String prefix, String extension, String content) {
    }

    public record Output(String fileName, String filePath) {
    }
}

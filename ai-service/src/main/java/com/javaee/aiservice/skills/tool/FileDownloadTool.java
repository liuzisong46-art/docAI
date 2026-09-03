package com.javaee.aiservice.skills.tool;

import com.javaee.aiservice.security.BucketPermissionService;
import com.javaee.aiservice.security.RequestUserContext;
import com.javaee.aiservice.service.MinIOService;
import com.javaee.common.utils.UserBucketUtils;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/** Atomic MinIO download operation. */
@Component
public class FileDownloadTool implements SkillTool<FileDownloadTool.Input, FileDownloadTool.Output> {

    private final MinIOService minIOService;
    private final BucketPermissionService bucketPermissionService;
    private final RequestUserContext requestUserContext;

    public FileDownloadTool(MinIOService minIOService, BucketPermissionService bucketPermissionService,
                            RequestUserContext requestUserContext) {
        this.minIOService = minIOService;
        this.bucketPermissionService = bucketPermissionService;
        this.requestUserContext = requestUserContext;
    }

    @Override
    public String id() {
        return "file-download";
    }

    @Override
    public Output execute(Input input) {
        if (input == null || input.objectName() == null || input.objectName().isBlank()) {
            throw new IllegalArgumentException("对象名称不能为空");
        }
        String bucket = input.bucketName() == null || input.bucketName().isBlank()
                ? UserBucketUtils.bucketNameForUser(requestUserContext.getRequiredUserId())
                : input.bucketName();
        try {
            if (!minIOService.bucketExists(bucket)) {
                throw new IllegalArgumentException("桶不存在: " + bucket);
            }
            bucketPermissionService.assertCanAccess(bucket);
            io.minio.StatObjectResponse metadata = minIOService.getFileMetadata(bucket, input.objectName());
            InputStream stream = minIOService.downloadFile(bucket, input.objectName());
            return new Output(stream, metadata.contentType(), input.objectName(), bucket);
        } catch (Exception e) {
            throw new RuntimeException("文件下载失败: " + e.getMessage(), e);
        }
    }

    public record Input(String objectName, String bucketName) {
    }

    public record Output(InputStream inputStream, String contentType, String objectName, String bucketName) {
    }
}

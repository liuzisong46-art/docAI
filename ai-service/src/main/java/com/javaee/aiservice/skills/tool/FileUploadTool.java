package com.javaee.aiservice.skills.tool;

import com.javaee.aiservice.security.BucketPermissionService;
import com.javaee.aiservice.security.RequestUserContext;
import com.javaee.aiservice.service.MinIOService;
import com.javaee.common.utils.UserBucketUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Atomic MinIO upload operation. */
@Component
public class FileUploadTool implements SkillTool<FileUploadTool.Input, Object> {

    private final MinIOService minIOService;
    private final BucketPermissionService bucketPermissionService;
    private final RequestUserContext requestUserContext;

    public FileUploadTool(MinIOService minIOService, BucketPermissionService bucketPermissionService,
                          RequestUserContext requestUserContext) {
        this.minIOService = minIOService;
        this.bucketPermissionService = bucketPermissionService;
        this.requestUserContext = requestUserContext;
    }

    @Override
    public String id() {
        return "file-upload";
    }

    @Override
    public Object execute(Input input) {
        if (input == null || input.file() == null) {
            throw new IllegalArgumentException("文件不能为空");
        }
        String bucket = input.bucketName() == null || input.bucketName().isBlank()
                ? UserBucketUtils.bucketNameForUser(requestUserContext.getRequiredUserId())
                : input.bucketName();
        try {
            bucketPermissionService.assertCanAccess(bucket);
            return minIOService.uploadFile(input.file(), bucket, input.objectName());
        } catch (Exception e) {
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    public record Input(MultipartFile file, String bucketName, String objectName) {
    }
}

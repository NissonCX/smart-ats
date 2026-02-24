package com.smartats;

import com.smartats.infrastructure.storage.FileStorageService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MinIO 集成测试
 * <p>
 * 需要 Docker 环境运行 MinIO 容器。
 * CI 环境可通过设置环境变量 CI=true 自动跳过。
 * 也可通过 Maven 排除：-Dtest='!MinioFileStorageServiceTest'
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
@DisabledIfEnvironmentVariable(named = "CI", matches = "true", disabledReason = "MinIO 集成测试需要 Docker 环境")
class MinioFileStorageServiceTest {

    @Autowired
    private FileStorageService fileStorageService;

    @Test
    void testEnsureBucketExists() {
        // 应该不抛异常
        assertDoesNotThrow(() -> fileStorageService.ensureBucketExists());
    }

    @Test
    void testUploadAndDeleteFile() throws Exception {
        // 准备测试文件
        String content = "Hello, MinIO!";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                content.getBytes(StandardCharsets.UTF_8)
        );

        String objectName = "test/" + System.currentTimeMillis() + "/test.txt";

        // 测试上传
        String url = fileStorageService.uploadFile(file, objectName);
        assertNotNull(url);
        assertTrue(url.contains(objectName));

        // 测试文件存在
        assertTrue(fileStorageService.fileExists(objectName));

        // 测试获取 URL
        String fileUrl = fileStorageService.getFileUrl(objectName);
        assertNotNull(fileUrl);

        // 测试删除
        assertDoesNotThrow(() -> fileStorageService.deleteFile(objectName));

        // 测试文件不存在
        assertFalse(fileStorageService.fileExists(objectName));
    }

    @Test
    void testGetPresignedUrl() {
        String objectName = "test/presigned.txt";

        String url = fileStorageService.getPresignedUrl(objectName, 3600);
        assertNotNull(url);
        assertTrue(url.contains("X-Amz-Expires"));
    }
}
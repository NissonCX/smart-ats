package com.smartats.infrastructure.storage;

import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioFileStorageService implements FileStorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${minio.endpoint}")
    private String endpoint;

    @Override
    public String uploadFile(MultipartFile file, String objectName) {
        try {
            InputStream inputStream = file.getInputStream();
            long size = file.getSize();
            String contentType = file.getContentType();

            return uploadFile(inputStream, objectName, size, contentType);
        } catch (Exception e) {
            log.error("上传文件失败: objectName={}", objectName, e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @Override
    public String uploadFile(InputStream inputStream, String objectName, long size, String contentType) {
        try {
            // 确保 Bucket 存在
            ensureBucketExists();

            // 上传文件
            PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(inputStream, size, -1)  // -1 表示不使用 part 上传
                    .contentType(contentType)
                    .build();

            minioClient.putObject(putObjectArgs);

            log.info("文件上传成功: bucket={}, object={}", bucketName, objectName);

            // 返回访问路径
            return getFileUrl(objectName);

        } catch (Exception e) {
            log.error("上传文件流失败: objectName={}", objectName, e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @Override
    public void deleteFile(String objectName) {
        try {
            RemoveObjectArgs removeObjectArgs = RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build();

            minioClient.removeObject(removeObjectArgs);

            log.info("文件删除成功: objectName={}", objectName);

        } catch (Exception e) {
            log.error("删除文件失败: objectName={}", objectName, e);
            throw new RuntimeException("文件删除失败", e);
        }
    }

    @Override
    public String getFileUrl(String objectName) {
        // 公共访问 URL（需要 Bucket 设置为 Public）
        return String.format("%s/%s/%s", endpoint, bucketName, objectName);
    }

    @Override
    public String getPresignedUrl(String objectName, int expires) {
        try {
            GetPresignedObjectUrlArgs args = GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketName)
                    .object(objectName)
                    .expiry(expires, TimeUnit.SECONDS)
                    .build();

            return minioClient.getPresignedObjectUrl(args);

        } catch (Exception e) {
            log.error("生成预签名URL失败: objectName={}", objectName, e);
            throw new RuntimeException("生成预签名URL失败", e);
        }
    }

    @Override
    public boolean fileExists(String objectName) {
        try {
            StatObjectArgs args = StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build();

            minioClient.statObject(args);
            return true;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 设置 Bucket 为公共读取（仅开发环境）
     * 生产环境建议使用预签名 URL
     */
    private void setBucketPublic() {
        try {
            // 构建 Bucket 策略（只读）
            String policy = String.format(
                    "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::%s/*\"]}]}",
                    bucketName
            );

            minioClient.setBucketPolicy(
                    SetBucketPolicyArgs.builder()
                            .bucket(bucketName)
                            .config(policy)
                            .build()
            );

            log.info("设置 Bucket 公共读取策略成功: {}", bucketName);

        } catch (Exception e) {
            // 如果策略已存在或设置失败，只记录警告，不影响程序运行
            log.warn("设置 Bucket 公共策略失败（可能是已设置）: bucket={}, error={}", bucketName, e.getMessage());
        }
    }

    @Override
    public void ensureBucketExists() {
        try {
            // 检查 Bucket 是否存在
            boolean found = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(bucketName)
                            .build()
            );

            if (!found) {
                // 创建 Bucket
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(bucketName)
                                .build()
                );
                log.info("创建 Bucket 成功: {}", bucketName);
            }

            // 设置 Bucket 为公共读取（开发环境）
            // 注意：每次启动都会尝试设置，如果已设置会跳过
            setBucketPublic();

        } catch (Exception e) {
            log.error("检查/创建 Bucket 失败: bucket={}", bucketName, e);
            throw new RuntimeException("Bucket 初始化失败", e);
        }
    }
}
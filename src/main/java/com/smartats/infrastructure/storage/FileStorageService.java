package com.smartats.infrastructure.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 文件存储服务接口
 * 设计原则：面向接口编程，方便后续切换到阿里云 OSS
 */
public interface FileStorageService {

    /**
     * 上传文件
     *
     * @param file       文件
     * @param objectName 对象名（文件路径）
     * @return 文件访问路径
     */
    String uploadFile(MultipartFile file, String objectName);

    /**
     * 上传文件流
     *
     * @param inputStream 文件流
     * @param objectName  对象名
     * @param size        文件大小
     * @param contentType 文件类型
     * @return 文件访问路径
     */
    String uploadFile(InputStream inputStream, String objectName, long size, String contentType);

    /**
     * 删除文件
     *
     * @param objectName 对象名
     */
    void deleteFile(String objectName);

    /**
     * 获取文件访问 URL
     *
     * @param objectName 对象名
     * @return 访问 URL
     */
    String getFileUrl(String objectName);

    /**
     * 生成预签名 URL（临时访问链接）
     *
     * @param objectName 对象名
     * @param expires    过期时间（秒）
     * @return 预签名 URL
     */
    String getPresignedUrl(String objectName, int expires);

    /**
     * 检查文件是否存在
     *
     * @param objectName 对象名
     * @return 是否存在
     */
    boolean fileExists(String objectName);

    /**
     * 确保 Bucket 存在
     */
    void ensureBucketExists();
}
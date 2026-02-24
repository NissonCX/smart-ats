package com.smartats.module.resume.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量上传简历响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchUploadResponse {

    /** 总文件数 */
    private int totalCount;

    /** 成功数 */
    private int successCount;

    /** 失败数 */
    private int failedCount;

    /** 每个文件的处理结果 */
    private List<BatchUploadItem> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchUploadItem {
        /** 任务ID（成功时有值） */
        private String taskId;

        /** 简历ID */
        private Long resumeId;

        /** 原始文件名 */
        private String fileName;

        /** 状态：QUEUED / DUPLICATE / FAILED */
        private String status;

        /** 描述信息 */
        private String message;
    }
}

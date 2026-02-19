package com.smartats.module.resume.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("resumes")
public class Resume {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String fileName;
    private String filePath;
    private String fileUrl;
    private Long fileSize;
    private String fileHash;
    private String fileType;
    private String status;  // PARSING, COMPLETED, FAILED
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
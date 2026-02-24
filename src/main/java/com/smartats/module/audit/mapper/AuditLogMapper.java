package com.smartats.module.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartats.module.audit.entity.AuditLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审计日志 Mapper 接口
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogEntity> {
}

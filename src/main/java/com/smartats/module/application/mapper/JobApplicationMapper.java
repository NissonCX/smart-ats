package com.smartats.module.application.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartats.module.application.entity.JobApplication;
import org.apache.ibatis.annotations.Mapper;

/**
 * 职位申请 Mapper
 */
@Mapper
public interface JobApplicationMapper extends BaseMapper<JobApplication> {
}

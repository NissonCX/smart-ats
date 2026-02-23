package com.smartats.module.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartats.module.interview.entity.InterviewRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 面试记录 Mapper
 */
@Mapper
public interface InterviewRecordMapper extends BaseMapper<InterviewRecord> {
}

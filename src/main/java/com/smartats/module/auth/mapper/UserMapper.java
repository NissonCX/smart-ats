package com.smartats.module.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartats.module.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper 接口
 *
 * @Mapper: MyBatis-Plus 扫描注解
 * BaseMapper: 提供 CRUD 基础方法
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}

package com.smartats.module.job.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.constants.RedisKeyConstants;
import com.smartats.common.enums.JobStatus;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.module.job.dto.request.*;
import com.smartats.module.job.dto.response.JobResponse;
import com.smartats.module.job.entity.Job;
import com.smartats.module.job.mapper.JobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final JobMapper jobMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheEvictionService cacheEvictionService;

    /**
     * 创建职位
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createJob(CreateJobRequest request, Long creatorId) {
        log.info("创建职位：title={}, creatorId={}", request.getTitle(), creatorId);

        Job job = new Job();
        BeanUtils.copyProperties(request, job);
        job.setCreatorId(creatorId);
        job.setStatus(JobStatus.DRAFT.getCode());
        job.setViewCount(0);

        // 手动设置时间（暂时代替自动填充）
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());

        // JSON 序列化：List<String> -> JSON 字符串
        if (request.getRequiredSkills() != null && !request.getRequiredSkills().isEmpty()) {
            try {
                job.setRequiredSkills(objectMapper.writeValueAsString(request.getRequiredSkills()));
            } catch (JsonProcessingException e) {
                log.error("技能标签序列化失败", e);
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "技能标签格式错误");
            }
        }

        int result = jobMapper.insert(job);
        if (result <= 0) {
            log.error("职位创建失败：title={}", request.getTitle());
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "职位创建失败");
        }

        log.info("职位创建成功：id={}, title={}", job.getId(), job.getTitle());
        return job.getId();
    }

    /**
     * 更新职位（延迟双删）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateJob(UpdateJobRequest request, Long operatorId) {
        log.info("更新职位：id={}, operatorId={}", request.getId(), operatorId);

        String cacheKey = RedisKeyConstants.CACHE_JOB_KEY_PREFIX + request.getId();

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 1 步：第一次删除缓存（更新前）
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        redisTemplate.delete(cacheKey);
        log.debug("第 1 次删除缓存：key={}", cacheKey);

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 2 步：查询职位是否存在
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        Job job = jobMapper.selectById(request.getId());
        if (job == null) {
            log.warn("职位不存在：id={}", request.getId());
            throw new BusinessException(ResultCode.NOT_FOUND, "职位不存在");
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 3 步：检查权限
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        if (!job.getCreatorId().equals(operatorId)) {
            log.warn("无权限修改职位：jobId={}, creatorId={}, operatorId={}", request.getId(), job.getCreatorId(), operatorId);
            throw new BusinessException(ResultCode.FORBIDDEN, "无权限修改此职位");
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 4 步：更新职位信息
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        if (StringUtils.hasText(request.getTitle())) {
            job.setTitle(request.getTitle());
        }
        if (StringUtils.hasText(request.getDepartment())) {
            job.setDepartment(request.getDepartment());
        }
        if (StringUtils.hasText(request.getDescription())) {
            job.setDescription(request.getDescription());
        }
        if (StringUtils.hasText(request.getRequirements())) {
            job.setRequirements(request.getRequirements());
        }
        if (request.getRequiredSkills() != null) {
            try {
                job.setRequiredSkills(objectMapper.writeValueAsString(request.getRequiredSkills()));
            } catch (JsonProcessingException e) {
                log.error("技能标签序列化失败", e);
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "技能标签格式错误");
            }
        }
        if (request.getSalaryMin() != null) {
            job.setSalaryMin(request.getSalaryMin());
        }
        if (request.getSalaryMax() != null) {
            job.setSalaryMax(request.getSalaryMax());
        }
        if (request.getExperienceMin() != null) {
            job.setExperienceMin(request.getExperienceMin());
        }
        if (request.getExperienceMax() != null) {
            job.setExperienceMax(request.getExperienceMax());
        }
        if (request.getEducation() != null) {
            job.setEducation(request.getEducation());
        }
        if (request.getJobType() != null) {
            job.setJobType(request.getJobType());
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 5 步：更新数据库
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        int result = jobMapper.updateById(job);
        if (result <= 0) {
            log.error("职位更新失败：id={}", request.getId());
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "职位更新失败");
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 6 步：延迟双删（异步删除缓存，通过独立 Service 保证代理生效）
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        cacheEvictionService.asyncDeleteCache(cacheKey);

        log.info("职位更新成功：id={}", job.getId());
    }

    /**
     * 获取职位详情（使用 Redis 原子计数器优化浏览量统计）
     * <p>
     * 浏览量计算公式：用户看到的浏览量 = 数据库累积值 + Redis增量计数器
     */
    public JobResponse getJobDetail(Long id) {
        log.info("查询职位详情：id={}", id);

        String cacheKey = RedisKeyConstants.CACHE_JOB_KEY_PREFIX + id;
        String counterKey = RedisKeyConstants.COUNTER_JOB_VIEW_PREFIX + id;

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 1 步：原子自增 Redis 增量计数器（每次访问都计数）
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        Long redisIncrement = redisTemplate.opsForValue().increment(counterKey);

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 2 步：尝试从缓存读取职位信息（缓存命中时跟过 DB）
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        String cachedJson = redisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null) {
            try {
                JobResponse cached = objectMapper.readValue(cachedJson, JobResponse.class);
                // 缓存中的浏览量可能已过时，用缓存的 baseViewCount + 当前 Redis 增量覆盖
                cached.setViewCount(cached.getViewCount() != null ? cached.getViewCount() + 1 : redisIncrement.intValue());
                log.debug("缓存命中：jobId={}", id);
                return cached;
            } catch (Exception e) {
                log.warn("职位缓存反序列化失败，降级查库：id={}", id, e);
            }
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 3 步：缓存未命中，从数据库读取
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        Job job = jobMapper.selectById(id);
        if (job == null) {
            log.warn("职位不存在：id={}", id);
            throw new BusinessException(ResultCode.NOT_FOUND, "职位不存在");
        }

        // 数据库中存储的是历史累积浏览量
        int baseViewCount = job.getViewCount() != null ? job.getViewCount() : 0;

        log.debug("Redis 计数器自增：key={}, increment={}, baseViewCount={}",
            counterKey, redisIncrement, baseViewCount);

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 4 步：计算总浏览量并回填缓存
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        JobResponse response = convertToResponse(job);

        // 总浏览量 = 数据库累积值 + Redis增量计数器
        response.setViewCount(baseViewCount + redisIncrement.intValue());

        try {
            redisTemplate.opsForValue().set(cacheKey,
                objectMapper.writeValueAsString(response), 30, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            log.error("缓存数据序列化失败", e);
        }

        return response;
    }

    /**
     * 职位列表（分页、筛选）
     */
    public Page<JobResponse> getJobList(JobQueryRequest request) {
        log.info("查询职位列表：{}", request);

        LambdaQueryWrapper<Job> queryWrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(request.getKeyword())) {
            queryWrapper.and(wrapper -> wrapper.like(Job::getTitle, request.getKeyword()).or().like(Job::getDescription, request.getKeyword()).or().like(Job::getRequirements, request.getKeyword()));
        }

        if (StringUtils.hasText(request.getDepartment())) {
            queryWrapper.eq(Job::getDepartment, request.getDepartment());
        }

        if (StringUtils.hasText(request.getJobType())) {
            queryWrapper.eq(Job::getJobType, request.getJobType());
        }

        if (StringUtils.hasText(request.getEducation())) {
            queryWrapper.eq(Job::getEducation, request.getEducation());
        }

        if (request.getExperienceMin() != null) {
            queryWrapper.le(Job::getExperienceMin, request.getExperienceMin());
        }

        if (request.getSalaryMin() != null) {
            queryWrapper.ge(Job::getSalaryMin, request.getSalaryMin());
        }

        if (StringUtils.hasText(request.getStatus())) {
            queryWrapper.eq(Job::getStatus, request.getStatus());
        } else {
            queryWrapper.eq(Job::getStatus, JobStatus.PUBLISHED.getCode());
        }

        // 排序
        if ("created_at".equals(request.getOrderBy())) {
            if ("asc".equals(request.getOrderDirection())) {
                queryWrapper.orderByAsc(Job::getCreatedAt);
            } else {
                queryWrapper.orderByDesc(Job::getCreatedAt);
            }
        } else if ("salary_max".equals(request.getOrderBy())) {
            if ("asc".equals(request.getOrderDirection())) {
                queryWrapper.orderByAsc(Job::getSalaryMax);
            } else {
                queryWrapper.orderByDesc(Job::getSalaryMax);
            }
        } else if ("view_count".equals(request.getOrderBy())) {
            if ("asc".equals(request.getOrderDirection())) {
                queryWrapper.orderByAsc(Job::getViewCount);
            } else {
                queryWrapper.orderByDesc(Job::getViewCount);
            }
        }

        Page<Job> page = new Page<>(request.getPageNum(), request.getPageSize());
        Page<Job> jobPage = jobMapper.selectPage(page, queryWrapper);

        Page<JobResponse> responsePage = new Page<>(jobPage.getCurrent(), jobPage.getSize(), jobPage.getTotal());
        List<JobResponse> responseList = jobPage.getRecords().stream().map(this::convertToResponse).collect(Collectors.toList());

        responsePage.setRecords(responseList);

        return responsePage;
    }

    /**
     * 发布职位
     */
    @Transactional(rollbackFor = Exception.class)
    public void publishJob(Long id, Long operatorId) {
        log.info("发布职位：id={}, operatorId={}", id, operatorId);

        Job job = jobMapper.selectById(id);
        if (job == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "职位不存在");
        }

        if (!job.getCreatorId().equals(operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权限操作此职位");
        }

        if (JobStatus.PUBLISHED.getCode().equals(job.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "职位已经是发布状态");
        }

        job.setStatus(JobStatus.PUBLISHED.getCode());
        jobMapper.updateById(job);

        // 清除缓存
        redisTemplate.delete(RedisKeyConstants.CACHE_JOB_KEY_PREFIX + id);

        log.info("职位发布成功：id={}", id);
    }

    /**
     * 关闭职位
     */
    @Transactional(rollbackFor = Exception.class)
    public void closeJob(Long id, Long operatorId) {
        log.info("关闭职位：id={}, operatorId={}", id, operatorId);

        Job job = jobMapper.selectById(id);
        if (job == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "职位不存在");
        }

        if (!job.getCreatorId().equals(operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权限操作此职位");
        }

        if (JobStatus.CLOSED.getCode().equals(job.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "职位已经是关闭状态");
        }

        job.setStatus(JobStatus.CLOSED.getCode());
        jobMapper.updateById(job);

        // 清除缓存
        redisTemplate.delete(RedisKeyConstants.CACHE_JOB_KEY_PREFIX + id);

        log.info("职位关闭成功：id={}", id);
    }

    /**
     * 删除职位（软删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteJob(Long id, Long operatorId) {
        log.info("删除职位：id={}, operatorId={}", id, operatorId);

        Job job = jobMapper.selectById(id);
        if (job == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "职位不存在");
        }

        if (!job.getCreatorId().equals(operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权限操作此职位");
        }

        int result = jobMapper.deleteById(id);
        if (result <= 0) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "职位删除失败");
        }

        // 清除缓存
        redisTemplate.delete(RedisKeyConstants.CACHE_JOB_KEY_PREFIX + id);

        log.info("职位删除成功：id={}", id);
    }

    /**
     * 热门职位列表
     */
    public List<JobResponse> getHotJobs(Integer limit) {
        log.info("查询热门职位：limit={}", limit);

        // 安全边界校验，避免使用 .last() 拼接 SQL
        int safeLimit = (limit != null && limit > 0 && limit <= 50) ? limit : 10;

        Page<Job> page = new Page<>(1, safeLimit);
        LambdaQueryWrapper<Job> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Job::getStatus, JobStatus.PUBLISHED.getCode()).orderByDesc(Job::getViewCount);

        Page<Job> resultPage = jobMapper.selectPage(page, queryWrapper);
        return resultPage.getRecords().stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    /**
     * 转换为响应对象
     */
    private JobResponse convertToResponse(Job job) {
        JobResponse response = new JobResponse();
        BeanUtils.copyProperties(job, response);

        // JSON 反序列化：JSON 字符串 -> List<String>
        if (StringUtils.hasText(job.getRequiredSkills())) {
            try {
                response.setRequiredSkills(java.util.Arrays.stream(objectMapper.readValue(job.getRequiredSkills(), String[].class)).collect(Collectors.toList()));
            } catch (JsonProcessingException e) {
                log.error("技能标签反序列化失败", e);
            }
        }

        // 格式化显示
        if (job.getSalaryMin() != null && job.getSalaryMax() != null) {
            response.setSalaryRange(job.getSalaryMin() + "K-" + job.getSalaryMax() + "K");
        }

        if (job.getExperienceMin() != null && job.getExperienceMax() != null) {
            response.setExperienceRange(job.getExperienceMin() + "-" + job.getExperienceMax() + "年");
        }

        // 状态描述（委托给枚举）
        response.setStatusDesc(JobStatus.getDescriptionByCode(job.getStatus()));

        return response;
    }
}
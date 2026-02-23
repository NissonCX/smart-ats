package com.smartats.module.candidate.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.constants.RedisKeyConstants;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.module.candidate.dto.CandidateQueryRequest;
import com.smartats.module.candidate.dto.CandidateUpdateRequest;
import com.smartats.module.candidate.entity.Candidate;
import com.smartats.module.candidate.mapper.CandidateMapper;
import com.smartats.module.resume.dto.CandidateInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 候选人服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateService {

    private final CandidateMapper candidateMapper;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    /** 候选人缓存 TTL（分钟） */
    private static final long CACHE_TTL_MINUTES = 30;

    /**
     * 创建候选人记录
     */
    @Transactional(rollbackFor = Exception.class)
    public Candidate createCandidate(Long resumeId, CandidateInfo candidateInfo, String rawJson) {
        log.info("创建候选人记录: resumeId={}, name={}", resumeId, candidateInfo.getName());

        // 检查是否已存在
        LambdaQueryWrapper<Candidate> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Candidate::getResumeId, resumeId);
        Candidate existing = candidateMapper.selectOne(queryWrapper);

        if (existing != null) {
            log.info("候选人已存在，更新记录: candidateId={}", existing.getId());
            return updateCandidate(existing.getId(), candidateInfo, rawJson);
        }

        Candidate candidate = buildFromCandidateInfo(new Candidate(), candidateInfo, rawJson);
        candidate.setResumeId(resumeId);
        LocalDateTime now = LocalDateTime.now();
        candidate.setParsedAt(now);
        candidate.setCreatedAt(now);
        candidate.setUpdatedAt(now);

        candidateMapper.insert(candidate);
        log.info("候选人记录创建成功: candidateId={}", candidate.getId());
        return candidate;
    }

    /**
     * 更新候选人记录
     */
    @Transactional(rollbackFor = Exception.class)
    public Candidate updateCandidate(Long id, CandidateInfo candidateInfo, String rawJson) {
        log.info("更新候选人记录: candidateId={}, name={}", id, candidateInfo.getName());

        Candidate candidate = candidateMapper.selectById(id);
        if (candidate == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "候选人不存在: id=" + id);
        }

        buildFromCandidateInfo(candidate, candidateInfo, rawJson);
        candidate.setParsedAt(LocalDateTime.now());
        candidate.setUpdatedAt(LocalDateTime.now());

        candidateMapper.updateById(candidate);
        evictCache(id);

        log.info("候选人记录更新成功: candidateId={}", id);
        return candidateMapper.selectById(id);
    }

    /**
     * 根据 resumeId 查询候选人
     */
    public Candidate getByResumeId(Long resumeId) {
        LambdaQueryWrapper<Candidate> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Candidate::getResumeId, resumeId);
        return candidateMapper.selectOne(queryWrapper);
    }

    /**
     * 根据 ID 查询候选人详情，优先从 Redis 缓存读取
     */
    public Candidate getById(Long id) {
        String cacheKey = RedisKeyConstants.CACHE_CANDIDATE_KEY_PREFIX + id;

        // 1. 查缓存
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, Candidate.class);
            } catch (JsonProcessingException e) {
                log.warn("候选人缓存反序列化失败，降级查库: id={}", id, e);
            }
        }

        // 2. 查数据库
        Candidate candidate = candidateMapper.selectById(id);
        if (candidate == null) {
            return null;
        }

        // 3. 回填缓存
        try {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(candidate),
                    CACHE_TTL_MINUTES,
                    TimeUnit.MINUTES
            );
        } catch (JsonProcessingException e) {
            log.warn("候选人写入缓存失败（不影响业务）: id={}", id, e);
        }

        return candidate;
    }

    /**
     * 手动更新候选人（由 Controller 直接调用，字段已在外部设置好）
     */
    @Transactional(rollbackFor = Exception.class)
    public Candidate saveManual(Candidate candidate) {
        candidate.setUpdatedAt(LocalDateTime.now());
        candidateMapper.updateById(candidate);
        evictCache(candidate.getId());
        log.info("候选人手动更新成功: candidateId={}", candidate.getId());
        return getById(candidate.getId());
    }

    /**
     * 使用 DTO 部分更新候选人字段（仅更新非 null 字段）
     */
    @Transactional(rollbackFor = Exception.class)
    public Candidate updateManual(Long id, CandidateUpdateRequest request) {
        log.info("更新候选人信息: id={}", id);

        Candidate candidate = candidateMapper.selectById(id);
        if (candidate == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "候选人不存在");
        }

        // 仅更新非 null 字段
        if (request.getName() != null) candidate.setName(request.getName());
        if (request.getPhone() != null) candidate.setPhone(request.getPhone());
        if (request.getEmail() != null) candidate.setEmail(request.getEmail());
        if (request.getGender() != null) candidate.setGender(request.getGender());
        if (request.getAge() != null) candidate.setAge(request.getAge());
        if (request.getEducation() != null) candidate.setEducation(request.getEducation());
        if (request.getSchool() != null) candidate.setSchool(request.getSchool());
        if (request.getMajor() != null) candidate.setMajor(request.getMajor());
        if (request.getGraduationYear() != null) candidate.setGraduationYear(request.getGraduationYear());
        if (request.getWorkYears() != null) candidate.setWorkYears(request.getWorkYears());
        if (request.getCurrentCompany() != null) candidate.setCurrentCompany(request.getCurrentCompany());
        if (request.getCurrentPosition() != null) candidate.setCurrentPosition(request.getCurrentPosition());
        if (request.getSkills() != null) candidate.setSkills(request.getSkills());
        if (request.getSelfEvaluation() != null) candidate.setSelfEvaluation(request.getSelfEvaluation());

        candidate.setUpdatedAt(LocalDateTime.now());
        candidateMapper.updateById(candidate);
        evictCache(id);

        log.info("候选人更新成功: candidateId={}", id);
        return getById(id);
    }

    /**
     * 删除候选人
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long id) {
        candidateMapper.deleteById(id);
        evictCache(id);
        log.info("候选人删除成功: candidateId={}", id);
    }

    /**
     * 分页查询候选人列表，支持多维度高级筛选
     * <ul>
     *   <li>keyword  — 姓名/邮箱/公司/职位 模糊</li>
     *   <li>education — 学历精确匹配</li>
     *   <li>skill    — 技能 JSON_CONTAINS</li>
     *   <li>minWorkYears / maxWorkYears — 工作年限范围</li>
     *   <li>currentPosition — 当前职位关键字</li>
     * </ul>
     */
    public Page<Candidate> listCandidates(CandidateQueryRequest request) {
        int page = Math.max(1, request.getPage());
        int pageSize = Math.min(100, Math.max(1, request.getPageSize()));

        Page<Candidate> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<Candidate> wrapper = new LambdaQueryWrapper<>();

        // ① 通用关键字
        if (StringUtils.hasText(request.getKeyword())) {
            String kw = request.getKeyword().trim();
            wrapper.and(w -> w
                    .like(Candidate::getName, kw)
                    .or().like(Candidate::getEmail, kw)
                    .or().like(Candidate::getCurrentCompany, kw)
                    .or().like(Candidate::getCurrentPosition, kw)
            );
        }

        // ② 学历精确匹配
        if (StringUtils.hasText(request.getEducation())) {
            wrapper.eq(Candidate::getEducation, request.getEducation().trim());
        }

        // ③ 技能 JSON_CONTAINS
        if (StringUtils.hasText(request.getSkill())) {
            wrapper.apply("JSON_CONTAINS(skills, JSON_QUOTE({0}))", request.getSkill().trim());
        }

        // ④ 工作年限范围
        if (request.getMinWorkYears() != null) {
            wrapper.ge(Candidate::getWorkYears, request.getMinWorkYears());
        }
        if (request.getMaxWorkYears() != null) {
            wrapper.le(Candidate::getWorkYears, request.getMaxWorkYears());
        }

        // ⑤ 当前职位关键字
        if (StringUtils.hasText(request.getCurrentPosition())) {
            wrapper.like(Candidate::getCurrentPosition, request.getCurrentPosition().trim());
        }

        wrapper.orderByDesc(Candidate::getCreatedAt);
        return candidateMapper.selectPage(pageParam, wrapper);
    }

    /**
     * 删除候选人详情缓存（写后失效策略）
     */
    private void evictCache(Long id) {
        redisTemplate.delete(RedisKeyConstants.CACHE_CANDIDATE_KEY_PREFIX + id);
        log.debug("候选人缓存已失效: id={}", id);
    }

    /**
     * 将 CandidateInfo 字段填充到 Candidate 实体（复用于创建和更新场景）
     */
    private Candidate buildFromCandidateInfo(Candidate candidate, CandidateInfo info, String rawJson) {
        candidate.setName(info.getName());
        candidate.setPhone(info.getPhone());
        candidate.setEmail(info.getEmail());
        candidate.setGender(normalizeGender(info.getGender()));
        candidate.setAge(info.getAge());

        candidate.setEducation(info.getEducation());
        candidate.setSchool(info.getSchool());
        candidate.setMajor(info.getMajor());
        candidate.setGraduationYear(info.getGraduationYear());

        candidate.setWorkYears(info.getWorkYears());
        candidate.setCurrentCompany(info.getCurrentCompany());
        candidate.setCurrentPosition(info.getCurrentPosition());

        candidate.setSkills(info.getSkills());
        candidate.setWorkExperience(convertToMapList(info.getWorkExperience()));
        candidate.setProjectExperience(convertToMapList(info.getProjectExperience()));
        candidate.setSelfEvaluation(info.getSelfEvaluation());
        candidate.setRawJson(rawJson);
        return candidate;
    }

    /**
     * 将对象列表转换为 {@code List<Map<String, Object>>}（用于 JSON 字段存储）
     */
    private List<Map<String, Object>> convertToMapList(List<?> list) {
        if (list == null) {
            return null;
        }
        return list.stream()
                .map(obj -> {
                    try {
                        String json = objectMapper.writeValueAsString(obj);
                        return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
                    } catch (JsonProcessingException e) {
                        log.error("对象转换为 Map 失败: obj={}", obj, e);
                        return new HashMap<String, Object>();
                    }
                })
                .toList();
    }

    /**
     * 规范化 gender 字段为数据库 ENUM 合法值
     */
    private String normalizeGender(String raw) {
        if (raw == null || raw.isBlank()) {
            return "UNKNOWN";
        }
        return switch (raw.trim().toUpperCase()) {
            case "男", "MALE", "M", "BOY", "MAN" -> "MALE";
            case "女", "FEMALE", "F", "GIRL", "WOMAN" -> "FEMALE";
            default -> "UNKNOWN";
        };
    }
}

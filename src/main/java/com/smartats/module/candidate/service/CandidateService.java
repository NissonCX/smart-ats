package com.smartats.module.candidate.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.module.candidate.entity.Candidate;
import com.smartats.module.candidate.mapper.CandidateMapper;
import com.smartats.module.resume.dto.CandidateInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 候选人服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateService {

    private final CandidateMapper candidateMapper;
    private final ObjectMapper objectMapper;

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

        // 创建新记录
        Candidate candidate = new Candidate();
        candidate.setResumeId(resumeId);

        // 基本信息
        candidate.setName(candidateInfo.getName());
        candidate.setPhone(candidateInfo.getPhone());
        candidate.setEmail(candidateInfo.getEmail());
        candidate.setGender(normalizeGender(candidateInfo.getGender()));
        candidate.setAge(candidateInfo.getAge());

        // 教育信息
        candidate.setEducation(candidateInfo.getEducation());
        candidate.setSchool(candidateInfo.getSchool());
        candidate.setMajor(candidateInfo.getMajor());
        candidate.setGraduationYear(candidateInfo.getGraduationYear());

        // 工作信息
        candidate.setWorkYears(candidateInfo.getWorkYears());
        candidate.setCurrentCompany(candidateInfo.getCurrentCompany());
        candidate.setCurrentPosition(candidateInfo.getCurrentPosition());

        // 技能与经历
        candidate.setSkills(candidateInfo.getSkills());
        candidate.setWorkExperience(convertWorkExperience(candidateInfo));
        candidate.setProjectExperience(convertProjectExperience(candidateInfo));
        candidate.setSelfEvaluation(candidateInfo.getSelfEvaluation());

        // AI 解析元数据：直接存储 AI 原始响应
        candidate.setRawJson(rawJson);
        LocalDateTime now = LocalDateTime.now();
        candidate.setParsedAt(now);
        candidate.setCreatedAt(now);
        candidate.setUpdatedAt(now);

        // 保存
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
            throw new RuntimeException("候选人不存在");
        }

        // 更新字段
        candidate.setName(candidateInfo.getName());
        candidate.setPhone(candidateInfo.getPhone());
        candidate.setEmail(candidateInfo.getEmail());
        candidate.setGender(normalizeGender(candidateInfo.getGender()));
        candidate.setAge(candidateInfo.getAge());

        candidate.setEducation(candidateInfo.getEducation());
        candidate.setSchool(candidateInfo.getSchool());
        candidate.setMajor(candidateInfo.getMajor());
        candidate.setGraduationYear(candidateInfo.getGraduationYear());

        candidate.setWorkYears(candidateInfo.getWorkYears());
        candidate.setCurrentCompany(candidateInfo.getCurrentCompany());
        candidate.setCurrentPosition(candidateInfo.getCurrentPosition());

        candidate.setSkills(candidateInfo.getSkills());
        candidate.setWorkExperience(convertWorkExperience(candidateInfo));
        candidate.setProjectExperience(convertProjectExperience(candidateInfo));
        candidate.setSelfEvaluation(candidateInfo.getSelfEvaluation());

        // AI 解析元数据：直接存储 AI 原始响应
        candidate.setRawJson(rawJson);
        candidate.setParsedAt(LocalDateTime.now());
        candidate.setUpdatedAt(LocalDateTime.now());

        candidateMapper.updateById(candidate);

        log.info("候选人记录更新成功: candidateId={}", candidate.getId());
        return candidate;
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
     * 根据 ID 查询候选人
     */
    public Candidate getById(Long id) {
        return candidateMapper.selectById(id);
    }

    /**
     * 手动更新候选人（由 Controller 直接调用，字段已在外部设置好）
     */
    @Transactional(rollbackFor = Exception.class)
    public Candidate saveManual(Candidate candidate) {
        candidate.setUpdatedAt(LocalDateTime.now());
        candidateMapper.updateById(candidate);
        log.info("候选人手动更新成功: candidateId={}", candidate.getId());
        return candidateMapper.selectById(candidate.getId());
    }

    /**
     * 删除候选人
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long id) {
        candidateMapper.deleteById(id);
        log.info("候选人删除成功: candidateId={}", id);
    }

    /**
     * 分页查询候选人列表，支持关键字模糊搜索（姓名/手机/邮箱/公司/职位）
     */
    public Page<Candidate> listCandidates(String keyword, int page, int pageSize) {
        Page<Candidate> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<Candidate> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(Candidate::getName, keyword.trim())
                    .or().like(Candidate::getPhone, keyword.trim())
                    .or().like(Candidate::getEmail, keyword.trim())
                    .or().like(Candidate::getCurrentCompany, keyword.trim())
                    .or().like(Candidate::getCurrentPosition, keyword.trim())
            );
        }
        wrapper.orderByDesc(Candidate::getCreatedAt);
        return candidateMapper.selectPage(pageParam, wrapper);
    }

    /**
     * 转换工作经历
     */
    private java.util.List<java.util.Map<String, Object>> convertWorkExperience(CandidateInfo candidateInfo) {
        if (candidateInfo.getWorkExperience() == null) {
            return null;
        }
        return candidateInfo.getWorkExperience().stream()
                .map(this::convertToMap)
                .toList();
    }

    /**
     * 转换项目经历
     */
    private java.util.List<java.util.Map<String, Object>> convertProjectExperience(CandidateInfo candidateInfo) {
        if (candidateInfo.getProjectExperience() == null) {
            return null;
        }
        return candidateInfo.getProjectExperience().stream()
                .map(this::convertToMap)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> convertToMap(Object obj) {
        try {
            String json = objectMapper.writeValueAsString(obj);
            return objectMapper.readValue(json, java.util.Map.class);
        } catch (JsonProcessingException e) {
            log.error("对象转换失败: obj={}", obj, e);
            return new java.util.HashMap<>();
        }
    }

    /**
     * 规范化 gender 字段为数据库 ENUM 合法值
     * 将 AI 返回的中英文内山形式均转为 MALE / FEMALE / UNKNOWN
     */
    private String normalizeGender(String raw) {
        if (raw == null || raw.isBlank()) {
            return "UNKNOWN";
        }
        String val = raw.trim().toUpperCase();
        return switch (val) {
            case "男", "MALE", "M", "BOY", "MAN" -> "MALE";
            case "女", "FEMALE", "F", "GIRL", "WOMAN" -> "FEMALE";
            default -> "UNKNOWN";
        };
    }
}

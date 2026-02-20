package com.smartats.module.candidate.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartats.common.result.Result;
import com.smartats.module.candidate.dto.CandidateResponse;
import com.smartats.module.candidate.dto.CandidateUpdateRequest;
import com.smartats.module.candidate.entity.Candidate;
import com.smartats.module.candidate.service.CandidateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 候选人管理接口
 */
@Slf4j
@RestController
@RequestMapping("/candidates")
@RequiredArgsConstructor
public class CandidateController {

    private final CandidateService candidateService;

    /**
     * 根据 resumeId 查询候选人
     */
    @GetMapping("/resume/{resumeId}")
    public Result<CandidateResponse> getByResumeId(@PathVariable Long resumeId) {
        log.info("查询候选人: resumeId={}", resumeId);

        Candidate candidate = candidateService.getByResumeId(resumeId);
        if (candidate == null) {
            return Result.error(404, "候选人不存在");
        }

        CandidateResponse response = convertToResponse(candidate);
        return Result.success(response);
    }

    /**
     * 根据 ID 查询候选人详情
     */
    @GetMapping("/{id}")
    public Result<CandidateResponse> getById(@PathVariable Long id) {
        log.info("查询候选人详情: id={}", id);

        Candidate candidate = candidateService.getById(id);
        if (candidate == null) {
            return Result.error(404, "候选人不存在");
        }

        CandidateResponse response = convertToResponse(candidate);
        return Result.success(response);
    }

    /**
     * 更新候选人信息
     */
    @PutMapping("/{id}")
    public Result<CandidateResponse> updateCandidate(
            @PathVariable Long id,
            @RequestBody CandidateUpdateRequest request) {
        log.info("更新候选人信息: id={}, request={}", id, request);

        Candidate candidate = candidateService.getById(id);
        if (candidate == null) {
            return Result.error(404, "候选人不存在");
        }

        // 更新字段
        if (request.getName() != null) {
            candidate.setName(request.getName());
        }
        if (request.getPhone() != null) {
            candidate.setPhone(request.getPhone());
        }
        if (request.getEmail() != null) {
            candidate.setEmail(request.getEmail());
        }
        if (request.getGender() != null) {
            candidate.setGender(request.getGender());
        }
        if (request.getAge() != null) {
            candidate.setAge(request.getAge());
        }
        if (request.getEducation() != null) {
            candidate.setEducation(request.getEducation());
        }
        if (request.getSchool() != null) {
            candidate.setSchool(request.getSchool());
        }
        if (request.getMajor() != null) {
            candidate.setMajor(request.getMajor());
        }
        if (request.getGraduationYear() != null) {
            candidate.setGraduationYear(request.getGraduationYear());
        }
        if (request.getWorkYears() != null) {
            candidate.setWorkYears(request.getWorkYears());
        }
        if (request.getCurrentCompany() != null) {
            candidate.setCurrentCompany(request.getCurrentCompany());
        }
        if (request.getCurrentPosition() != null) {
            candidate.setCurrentPosition(request.getCurrentPosition());
        }
        if (request.getSkills() != null) {
            candidate.setSkills(request.getSkills());
        }
        if (request.getSelfEvaluation() != null) {
            candidate.setSelfEvaluation(request.getSelfEvaluation());
        }

        // 保存更新
        Candidate updated = candidateService.saveManual(candidate);

        CandidateResponse response = convertToResponse(updated);
        return Result.success(response);
    }

    /**
     * 删除候选人
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteCandidate(@PathVariable Long id) {
        log.info("删除候选人: id={}", id);
        Candidate candidate = candidateService.getById(id);
        if (candidate == null) {
            return Result.error(404, "候选人不存在");
        }
        candidateService.deleteById(id);
        return Result.success(null);
    }

    /**
     * 分页查询候选人列表
     */
    @GetMapping
    public Result<IPage<CandidateResponse>> listCandidates(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        log.info("查询候选人列表: page={}, pageSize={}, keyword={}", page, pageSize, keyword);

        Page<Candidate> result = candidateService.listCandidates(keyword, page, pageSize);

        Page<CandidateResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(this::convertToResponse).toList());

        return Result.success(responsePage);
    }

    private CandidateResponse convertToResponse(Candidate candidate) {
        CandidateResponse response = new CandidateResponse();
        BeanUtils.copyProperties(candidate, response);
        return response;
    }
}

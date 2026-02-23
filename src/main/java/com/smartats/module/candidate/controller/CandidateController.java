package com.smartats.module.candidate.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.Result;
import com.smartats.common.result.ResultCode;
import com.smartats.common.util.DataMaskUtil;
import com.smartats.module.candidate.dto.CandidateQueryRequest;
import com.smartats.module.candidate.dto.CandidateResponse;
import com.smartats.module.candidate.dto.CandidateUpdateRequest;
import com.smartats.module.candidate.entity.Candidate;
import com.smartats.module.candidate.service.CandidateService;
import jakarta.validation.Valid;
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
            throw new BusinessException(ResultCode.NOT_FOUND, "候选人不存在");
        }

        CandidateResponse response = toResponse(candidate);
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
            throw new BusinessException(ResultCode.NOT_FOUND, "候选人不存在");
        }

        CandidateResponse response = toResponse(candidate);
        return Result.success(response);
    }

    /**
     * 更新候选人信息（更新逻辑下沉到 Service 层）
     */
    @PutMapping("/{id}")
    public Result<CandidateResponse> updateCandidate(
            @PathVariable Long id,
            @Valid @RequestBody CandidateUpdateRequest request) {
        log.info("更新候选人信息: id={}", id);

        Candidate updated = candidateService.updateManual(id, request);
        CandidateResponse response = toResponse(updated);
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
            throw new BusinessException(ResultCode.NOT_FOUND, "候选人不存在");
        }

        candidateService.deleteById(id);
        return Result.success(null);
    }

    /**
     * 分页查询候选人列表，支持多维度筛选
     */
    @GetMapping
    public Result<IPage<CandidateResponse>> listCandidates(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String education,
            @RequestParam(required = false) String skill,
            @RequestParam(required = false) Integer minWorkYears,
            @RequestParam(required = false) Integer maxWorkYears,
            @RequestParam(required = false) String currentPosition) {
        log.info("查询候选人列表: page={}, pageSize={}, keyword={}, education={}, skill={}",
                page, pageSize, keyword, education, skill);

        CandidateQueryRequest request = new CandidateQueryRequest();
        request.setPage(page);
        request.setPageSize(pageSize);
        request.setKeyword(keyword);
        request.setEducation(education);
        request.setSkill(skill);
        request.setMinWorkYears(minWorkYears);
        request.setMaxWorkYears(maxWorkYears);
        request.setCurrentPosition(currentPosition);

        Page<Candidate> result = candidateService.listCandidates(request);

        Page<CandidateResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(this::toResponse).toList());

        return Result.success(responsePage);
    }

    /**
     * 将 Candidate 实体转换为 API 响应，并对敏感字段脱敏
     */
    private CandidateResponse toResponse(Candidate candidate) {
        CandidateResponse response = new CandidateResponse();
        BeanUtils.copyProperties(candidate, response);
        response.setPhone(DataMaskUtil.maskPhone(candidate.getPhone()));
        response.setEmail(DataMaskUtil.maskEmail(candidate.getEmail()));
        return response;
    }
}

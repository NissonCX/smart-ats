package com.smartats.module.resume.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.module.resume.dto.CandidateInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 简历 AI 解析服务（智谱 AI）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeParseService {

    private final OpenAiChatModel chatModel;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.openai.chat.options.model:glm-4-flash-250414}")
    private String model;

    public CandidateInfo parseResume(String resumeContent) {
        return parseResumeInternal(resumeContent).candidateInfo();
    }

    /**
     * 解析简历，返回候选人信息及 AI 原始响应（用于屘入 rawJson）
     */
    public ParseResult parseResumeWithRaw(String resumeContent) {
        return parseResumeInternal(resumeContent);
    }

    public record ParseResult(CandidateInfo candidateInfo, String rawResponse) {}

    private ParseResult parseResumeInternal(String resumeContent) {
        log.info("开始使用智谱 AI 解析简历: model={}, contentLength={}", model, resumeContent.length());

        // 内容预检：避免将空内容发给 AI
        if (resumeContent == null || resumeContent.trim().length() < 20) {
            log.error("简历文本内容过短或为空，无法解析。实际内容: [{}]", resumeContent);
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "简历内容提取失败，可能是扫描件或特殊编码 PDF，提取长度=" + (resumeContent == null ? 0 : resumeContent.trim().length()));
        }

        log.info("提取的简历文本前 300 字符: [{}]",
                resumeContent.trim().substring(0, Math.min(300, resumeContent.trim().length())));

        try {
            // 1. 构建 Prompt（针对中文简历优化）
            String prompt = buildPromptForChineseResume(resumeContent);

            // 2. 调用智谱 AI
            Prompt aiPrompt = new Prompt(new UserMessage(prompt));
            String responseContent = chatModel.call(aiPrompt).getResult().getOutput().getContent();

            log.info("智谱 AI 原始响应: {}", responseContent);

            // 3. 清理响应内容（移除可能的 markdown 代码块标记）
            String cleanedResponse = cleanMarkdownCodeBlock(responseContent);
            log.info("清理后的 JSON: {}", cleanedResponse);

            // 4. 解析 JSON 响应
            CandidateInfo candidateInfo = objectMapper.readValue(cleanedResponse, CandidateInfo.class);

            log.info("智谱 AI 解析成功: name={}, phone={}, email={}",
                    candidateInfo.getName(), candidateInfo.getPhone(), candidateInfo.getEmail());

            // 空结果预警
            if (!StringUtils.hasText(candidateInfo.getName()) && !StringUtils.hasText(candidateInfo.getPhone())) {
                log.warn("AI 解析结果所有关键字段均为 null，AI 响应可能格式异常。原始响应: {}", responseContent);
                throw new BusinessException(ResultCode.AI_SERVICE_ERROR,
                        "AI 解析返回结果为空，请检查日志。AI 原始响应: " + responseContent);
            }

            return new ParseResult(candidateInfo, responseContent);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("智谱 AI 解析失败", e);
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "简历解析失败: " + e.getMessage());
        }
    }

    private String buildPromptForChineseResume(String resumeContent) {
        return """
                你是一个专业的简历信息提取助手。请从以下中文简历内容中提取结构化信息，并以 JSON 格式返回。

                ## 提取字段说明

                ### 基本信息
                - name: 姓名
                - phone: 手机号（11位数字）
                - email: 邮箱地址
                - gender: 性别（男/女）
                - age: 年龄

                ### 教育信息
                - education: 学历（高中/专科/本科/硕士研究生/博士研究生）
                - school: 毕业院校
                - major: 专业
                - graduationYear: 毕业年份（4位整数）

                ### 工作信息
                - workYears: 工作年限（整数年）
                - currentCompany: 当前公司
                - currentPosition: 当前职位

                ### 技能与经历
                - skills: 技能列表（只保留技术技能）
                - workExperience: 工作经历数组，包含 company, position, startDate, endDate, description
                - projectExperience: 项目经历数组，包含 name, role, startDate, endDate, description, technologies
                - selfEvaluation: 自我评价

                ## 日期格式转换

                - "2020年1月" → "2020-01"
                - "2020.01" → "2020-01"
                - "至今" → "至今"

                ## 注意事项

                1. 无法提取的字段使用 null
                2. 日期格式统一为 yyyy-MM
                3. 技能列表只保留核心技术
                4. 工作经历按时间倒序
                5. 只返回 JSON，不包含 markdown 代码块标记
                6. workExperience 和 projectExperience 数组中的每个元素也使用 JSON 对象

                ## 简历内容

                %s

                请返回提取的 JSON（不要包含任何 markdown 标记）：
                """.formatted(resumeContent);
    }

    /**
     * 清理响应中的 markdown 代码块标记
     */
    private String cleanMarkdownCodeBlock(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        content = content.trim();

        // 移除开头的 ```json 或 ```
        if (content.startsWith("```json")) {
            content = content.substring(7);
        } else if (content.startsWith("```")) {
            content = content.substring(3);
        }

        // 移除结尾的 ```
        if (content.endsWith("```")) {
            content = content.substring(0, content.length() - 3);
        }

        return content.trim();
    }
}
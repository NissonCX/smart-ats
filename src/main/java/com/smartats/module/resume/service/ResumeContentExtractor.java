package com.smartats.module.resume.service;

import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URL;

/**
 * 简历内容提取服务
 * 从 PDF/DOC/DOCX 中提取纯文本
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeContentExtractor {

    /**
     * 从文件 URL 提取文本内容
     */
    public String extractText(String fileUrl, String fileType) {
        log.info("开始提取文件内容: fileUrl={}, fileType={}", fileUrl, fileType);

        try {
            URL url = new URL(fileUrl);
            InputStream inputStream = url.openStream();

            String text = switch (fileType) {
                case "application/pdf" -> extractFromPDF(inputStream);
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        -> extractFromDOCX(inputStream);
                case "application/msword" -> extractFromDOC(inputStream);
                default -> throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的文件类型");
            };

            inputStream.close();

            log.info("文件内容提取完成: fileType={}, textLength={}, 前200字符=[{}]",
                    fileType, text.length(),
                    text.trim().substring(0, Math.min(200, text.trim().length())));

            return text;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件内容提取失败: fileUrl={}", fileUrl, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "文件解析失败");
        }
    }

    private String extractFromPDF(InputStream inputStream) throws Exception {
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    private String extractFromDOCX(InputStream inputStream) throws Exception {
        StringBuilder text = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                text.append(paragraph.getText()).append("\n");
            }
        }
        return text.toString();
    }

    private String extractFromDOC(InputStream inputStream) throws Exception {
        try (HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }
}
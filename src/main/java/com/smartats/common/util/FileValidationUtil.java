package com.smartats.common.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 文件验证工具类
 * 通过文件头（魔数）验证真实文件类型，防止伪造文件类型攻击
 */
@Slf4j
public class FileValidationUtil {

    /**
     * 常见文件类型的魔数（文件头标识）
     * Key: 文件扩展名
     * Value: 文件头十六进制签名
     */
    private static final Map<String, String> FILE_SIGNATURES = new HashMap<>();

    static {
        // PDF: %PDF (25 50 44 46)
        FILE_SIGNATURES.put(".pdf", "25 50 44 46");

        // DOC (Microsoft Word 97-2003): D0 CF 11 E0 A1 B1 1A E1
        FILE_SIGNATURES.put(".doc", "D0 CF 11 E0");

        // DOCX (Microsoft Word 2007+): PK (50 4B 03 04) - ZIP 格式
        FILE_SIGNATURES.put(".docx", "50 4B 03 04");
    }

    /**
     * 获取文件的真实 MIME 类型（字节数组版本，推荐使用）
     *
     * @param fileBytes 文件字节数组
     * @param originalFilename 原始文件名（用于辅助判断）
     * @return 真实的 MIME 类型，如果无法识别则返回 null
     */
    public static String detectRealMimeType(byte[] fileBytes, String originalFilename) {
        if (fileBytes == null || fileBytes.length < 4) {
            return null;
        }
        byte[] header = fileBytes;
        return parseMimeType(header, originalFilename);
    }

    /**
     * 获取文件的真实 MIME 类型
     * 根据文件内容判断，而不是依赖文件扩展名或 Content-Type
     *
     * @param inputStream 文件输入流
     * @param originalFilename 原始文件名（用于辅助判断）
     * @return 真实的 MIME 类型，如果无法识别则返回 null
     */
    public static String detectRealMimeType(InputStream inputStream, String originalFilename) {
        try {
            byte[] header = readFileHeader(inputStream);

            return parseMimeType(header, originalFilename);

        } catch (IOException e) {
            log.error("读取文件头失败", e);
            return null;
        }
    }

    /**
     * 根据文件头字节解析 MIME 类型（内部公共逻辑）
     */
    private static String parseMimeType(byte[] header, String originalFilename) {
        // 检查是否为 PDF
        if (header[0] == 0x25 && header[1] == 0x50 && header[2] == 0x44 && header[3] == 0x46) {
            return "application/pdf";
        }

        // 检查是否为 DOC (旧版 Word)
        if (header[0] == (byte) 0xD0 && header[1] == (byte) 0xCF && header[2] == 0x11 && header[3] == (byte) 0xE0) {
            return "application/msword";
        }

        // 检查是否为 DOCX (新版 Word，实际是 ZIP 格式)
        if (header[0] == 0x50 && header[1] == 0x4B && header[2] == 0x03 && header[3] == 0x04) {
            if (originalFilename != null && originalFilename.toLowerCase().endsWith(".docx")) {
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            }
            return "application/zip";
        }

        log.warn("无法识别的文件类型，文件头: 0x{} 0x{} 0x{} 0x{}",
                Integer.toHexString(header[0] & 0xFF),
                Integer.toHexString(header[1] & 0xFF),
                Integer.toHexString(header[2] & 0xFF),
                Integer.toHexString(header[3] & 0xFF));
        return null;
    }

    /**
     * 验证文件是否为声明的类型（字节数组版本，推荐使用，避免流 mark/reset 问题）
     *
     * @param fileBytes 文件字节数组
     * @param declaredContentType 声明的 Content-Type
     * @param originalFilename 原始文件名
     * @return true 如果文件真实类型与声明一致
     */
    public static boolean validateFileType(byte[] fileBytes, String declaredContentType, String originalFilename) {
        String realMimeType = detectRealMimeType(fileBytes, originalFilename);
        return checkMimeMatch(realMimeType, declaredContentType, originalFilename);
    }

    /**
     * 验证文件是否为声明的类型
     *
     * @param inputStream 文件输入流
     * @param declaredContentType 声明的 Content-Type
     * @param originalFilename 原始文件名
     * @return true 如果文件真实类型与声明一致
     */
    public static boolean validateFileType(InputStream inputStream, String declaredContentType, String originalFilename) {
        String realMimeType = detectRealMimeType(inputStream, originalFilename);

        if (realMimeType == null) {
            return false;
        }

        return checkMimeMatch(realMimeType, declaredContentType, originalFilename);
    }

    private static boolean checkMimeMatch(String realMimeType, String declaredContentType, String originalFilename) {
        if (realMimeType == null) {
            return false;
        }
        boolean isValid = switch (declaredContentType) {
            case "application/pdf" -> realMimeType.equals("application/pdf");
            case "application/msword" -> realMimeType.equals("application/msword");
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                    realMimeType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            default -> false;
        };
        if (!isValid) {
            log.warn("文件类型不匹配: 声明={}, 真实={}, 文件名={}", declaredContentType, realMimeType, originalFilename);
        }
        return isValid;
    }

    /**
     * 读取文件头（前 8 字节），使用 BufferedInputStream 包装以支持 mark/reset
     */
    private static byte[] readFileHeader(InputStream inputStream) throws IOException {
        java.io.BufferedInputStream buffered = new java.io.BufferedInputStream(inputStream);
        byte[] header = new byte[8];
        buffered.mark(8);
        int bytesRead = buffered.read(header);
        buffered.reset();

        if (bytesRead < 4) {
            throw new IOException("文件太小，无法读取文件头");
        }

        return header;
    }

    /**
     * 消毒文件名，防止路径遍历攻击
     * - 移除路径分隔符
     * - 移除特殊字符
     * - 限制文件名长度
     *
     * @param filename 原始文件名
     * @return 消毒后的文件名
     */
    public static String sanitizeFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "unnamed";
        }

        // 移除路径信息（只保留文件名部分）
        String sanitized = filename.replaceAll(".*/", "")
                                   .replaceAll(".*\\\\", "");

        // 移除危险字符
        sanitized = sanitized.replaceAll("[\\x00-\\x1f\\x7f]", ""); // 控制字符
        sanitized = sanitized.replaceAll("[<>:\"|?*]", ""); // Windows 不允许的字符
        sanitized = sanitized.replaceAll("\\.\\.", ""); // 防止路径遍历

        // 限制文件名长度（255 字节是大多数文件系统的限制）
        if (sanitized.length() > 200) {
            String extension = "";
            int dotIndex = sanitized.lastIndexOf('.');
            if (dotIndex > 0) {
                extension = sanitized.substring(dotIndex);
                sanitized = sanitized.substring(0, 190) + extension;
            } else {
                sanitized = sanitized.substring(0, 200);
            }
        }

        if (sanitized.isEmpty()) {
            return "unnamed";
        }

        log.debug("文件名消毒: {} -> {}", filename, sanitized);
        return sanitized;
    }
}

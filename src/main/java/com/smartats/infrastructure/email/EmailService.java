package com.smartats.infrastructure.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 邮件发送服务
 * <p>
 * 功能：
 * 1. 发送验证码邮件
 * 2. 支持纯文本邮件
 * 3. 后续可扩展支持 HTML 邮件
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.verification-code.expire-time:300}")
    private Integer expireTimeSeconds;

    /**
     * 发送验证码邮件
     *
     * @param to   收件人邮箱
     * @param code 验证码
     * @return 是否发送成功
     */
    public boolean sendVerificationCode(String to, String code) {
        try {
            log.info("发送验证码邮件：to={}, code={}", to, code);

            // 创建邮件消息
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            // 设置发件人
            helper.setFrom(fromEmail);

            // 设置收件人
            helper.setTo(to);

            // 设置主题
            helper.setSubject("【SmartATS】注册验证码");

            // 设置邮件内容
            String content = buildEmailContent(code);
            helper.setText(content, true);  // true = HTML 格式

            // 发送邮件
            mailSender.send(message);

            log.info("验证码邮件发送成功：to={}", to);
            return true;

        } catch (MessagingException e) {
            log.error("验证码邮件发送失败：to={}, error={}", to, e.getMessage());
            return false;
        }
    }

    /**
     * 构建邮件内容（现代化、国际化设计）
     *
     * @param code 验证码
     * @return HTML 格式的邮件内容
     */
    private String buildEmailContent(String code) {
        int expireMinutes = expireTimeSeconds / 60;

        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>SmartATS Verification Code</title>
                <style>
                    /* ========== 全局样式 ========== */
                    * {
                        margin: 0;
                        padding: 0;
                        box-sizing: border-box;
                    }

                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
                        line-height: 1.6;
                        color: #2c3e50;
                        background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                        padding: 20px;
                        -webkit-font-smoothing: antialiased;
                        -moz-osx-font-smoothing: grayscale;
                    }

                    /* ========== 容器样式 ========== */
                    .email-container {
                        max-width: 600px;
                        margin: 0 auto;
                        background: #ffffff;
                        border-radius: 24px;
                        overflow: hidden;
                        box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
                    }

                    /* ========== 头部样式 ========== */
                    .header {
                        background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                        padding: 60px 40px;
                        text-align: center;
                        position: relative;
                        overflow: hidden;
                    }

                    .header::before {
                        content: '';
                        position: absolute;
                        top: -50%%;
                        left: -50%%;
                        width: 200%%;
                        height: 200%%;
                        background: radial-gradient(circle, rgba(255,255,255,0.1) 1px, transparent 1px);
                        background-size: 20px 20px;
                        animation: pattern-move 20s linear infinite;
                    }

                    @keyframes pattern-move {
                        0%% { transform: translate(0, 0); }
                        100%% { transform: translate(20px, 20px); }
                    }

                    .logo {
                        font-size: 48px;
                        font-weight: 700;
                        color: #ffffff;
                        letter-spacing: -1px;
                        margin-bottom: 10px;
                        position: relative;
                        z-index: 1;
                        text-shadow: 0 2px 10px rgba(0, 0, 0, 0.2);
                    }

                    .logo-icon {
                        display: inline-block;
                        margin-right: 10px;
                        font-size: 52px;
                    }

                    .tagline {
                        color: rgba(255, 255, 255, 0.9);
                        font-size: 16px;
                        font-weight: 300;
                        letter-spacing: 1px;
                        position: relative;
                        z-index: 1;
                    }

                    /* ========== 内容区域 ========== */
                    .content {
                        padding: 50px 40px;
                        background: #ffffff;
                    }

                    .greeting {
                        font-size: 24px;
                        font-weight: 600;
                        color: #2c3e50;
                        margin-bottom: 20px;
                    }

                    .message {
                        font-size: 16px;
                        color: #5a6c7d;
                        line-height: 1.8;
                        margin-bottom: 30px;
                    }

                    /* ========== 验证码卡片 ========== */
                    .code-card {
                        background: linear-gradient(135deg, #f6f8fb 0%%, #e9ecef 100%%);
                        border-radius: 16px;
                        padding: 35px;
                        margin: 35px 0;
                        text-align: center;
                        position: relative;
                        overflow: hidden;
                        border: 2px solid #e9ecef;
                    }

                    .code-card::before {
                        content: '';
                        position: absolute;
                        top: 0;
                        left: 0;
                        right: 0;
                        height: 4px;
                        background: linear-gradient(90deg, #667eea, #764ba2, #667eea);
                        background-size: 200%% 100%%;
                        animation: gradient-move 3s ease infinite;
                    }

                    @keyframes gradient-move {
                        0%%, 100%% { background-position: 0%% 50%%; }
                        50%% { background-position: 100%% 50%%; }
                    }

                    .code-label {
                        font-size: 14px;
                        font-weight: 600;
                        color: #6c757d;
                        text-transform: uppercase;
                        letter-spacing: 2px;
                        margin-bottom: 20px;
                    }

                    .verification-code {
                        font-family: 'Courier New', monospace;
                        font-size: 42px;
                        font-weight: 700;
                        color: #667eea;
                        letter-spacing: 12px;
                        margin: 20px 0;
                        text-shadow: 0 2px 4px rgba(102, 126, 234, 0.2);
                        position: relative;
                        z-index: 1;
                    }

                    .code-expire {
                        font-size: 14px;
                        color: #6c757d;
                        margin-top: 15px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        gap: 8px;
                    }

                    .code-expire-icon {
                        font-size: 16px;
                    }

                    /* ========== 提示信息 ========== */
                    .tips {
                        background: #fff3cd;
                        border-left: 4px solid #ffc107;
                        padding: 15px 20px;
                        margin: 30px 0;
                        border-radius: 8px;
                        font-size: 14px;
                        color: #856404;
                        line-height: 1.6;
                    }

                    .tips-icon {
                        margin-right: 8px;
                        font-size: 16px;
                    }

                    .security-note {
                        font-size: 14px;
                        color: #6c757d;
                        text-align: center;
                        margin: 30px 0;
                        padding: 20px;
                        background: #f8f9fa;
                        border-radius: 12px;
                        border: 1px dashed #dee2e6;
                    }

                    /* ========== 按钮样式 ========== */
                    .button {
                        display: inline-block;
                        padding: 15px 40px;
                        background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                        color: #ffffff;
                        text-decoration: none;
                        border-radius: 50px;
                        font-weight: 600;
                        font-size: 16px;
                        margin-top: 20px;
                        box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
                        transition: all 0.3s ease;
                    }

                    .button:hover {
                        transform: translateY(-2px);
                        box-shadow: 0 6px 20px rgba(102, 126, 234, 0.5);
                    }

                    /* ========== 底部样式 ========== */
                    .footer {
                        background: #f8f9fa;
                        padding: 30px 40px;
                        text-align: center;
                        border-top: 1px solid #e9ecef;
                    }

                    .footer-text {
                        font-size: 13px;
                        color: #6c757d;
                        line-height: 1.8;
                        margin: 5px 0;
                    }

                    .footer-links {
                        margin-top: 20px;
                        padding-top: 20px;
                        border-top: 1px solid #dee2e6;
                    }

                    .footer-link {
                        color: #667eea;
                        text-decoration: none;
                        font-size: 13px;
                        margin: 0 10px;
                        transition: color 0.3s ease;
                    }

                    .footer-link:hover {
                        color: #764ba2;
                        text-decoration: underline;
                    }

                    .social-icons {
                        margin-top: 20px;
                    }

                    .social-icon {
                        display: inline-block;
                        width: 36px;
                        height: 36px;
                        line-height: 36px;
                        border-radius: 50%%;
                        background: #e9ecef;
                        color: #6c757d;
                        text-align: center;
                        margin: 0 5px;
                        text-decoration: none;
                        transition: all 0.3s ease;
                    }

                    .social-icon:hover {
                        background: #667eea;
                        color: #ffffff;
                        transform: translateY(-2px);
                    }

                    /* ========== 响应式设计 ========== */
                    @media only screen and (max-width: 600px) {
                        body {
                            padding: 10px;
                        }

                        .header, .content, .footer {
                            padding: 30px 20px;
                        }

                        .logo {
                            font-size: 36px;
                        }

                        .verification-code {
                            font-size: 32px;
                            letter-spacing: 8px;
                        }

                        .button {
                            display: block;
                            text-align: center;
                        }
                    }
                </style>
            </head>
            <body>
                <div class="email-container">
                    <!-- 头部 -->
                    <div class="header">
                        <div class="logo">
                            <span class="logo-icon">🎯</span>
                            SmartATS
                        </div>
                        <div class="tagline">Intelligent Recruitment System</div>
                    </div>

                    <!-- 内容 -->
                    <div class="content">
                        <div class="greeting">👋 Hi there,</div>

                        <div class="message">
                            Thank you for choosing SmartATS! You're just one step away from accessing our intelligent recruitment platform.
                        </div>

                        <!-- 验证码卡片 -->
                        <div class="code-card">
                            <div class="code-label">🔐 Your Verification Code</div>
                            <div class="verification-code">%s</div>
                            <div class="code-expire">
                                <span class="code-expire-icon">⏰</span>
                                Valid for <strong>%d minutes</strong>
                            </div>
                        </div>

                        <!-- 提示信息 -->
                        <div class="tips">
                            <span class="tips-icon">💡</span>
                            <strong>Tip:</strong> Please enter this code on the registration page to complete your signup.
                        </div>

                        <!-- 安全提示 -->
                        <div class="security-note">
                            🔒 For your security, this code will expire after use. If you didn't request this code, please ignore this email.
                        </div>
                    </div>

                    <!-- 底部 -->
                    <div class="footer">
                        <p class="footer-text">
                            This is an automated email from SmartATS System.<br>
                            Please do not reply directly to this email.
                        </p>

                        <div class="footer-links">
                            <a href="https://smartats.com/privacy" class="footer-link">Privacy Policy</a>
                            <a href="https://smartats.com/terms" class="footer-link">Terms of Service</a>
                            <a href="https://smartats.com/support" class="footer-link">Contact Support</a>
                        </div>

                        <div class="social-icons">
                            <a href="#" class="social-icon">𝕏</a>
                            <a href="#" class="social-icon">in</a>
                            <a href="#" class="social-icon">📘</a>
                        </div>

                        <p class="footer-text" style="margin-top: 20px;">
                            &copy; 2026 SmartATS. All rights reserved.<br>
                            Building the future of intelligent recruitment.
                        </p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(code, expireMinutes);
    }
}

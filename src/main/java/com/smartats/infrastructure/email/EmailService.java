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
     * 构建邮件内容（Glassmorphism 风格，国际化设计）
     * 设计系统：
     * - 风格: Glassmorphism（玻璃态）
     * - 字体: Poppins + Open Sans
     * - 配色: 专业蓝 (#0369A1) + 天蓝 (#0EA5E9) + 成功绿 (#22C55E)
     * - 效果: 背景模糊、半透明、渐变、层次深度
     *
     * @param code 验证码
     * @return HTML 格式的邮件内容
     */
    private String buildEmailContent(String code) {
        int expireMinutes = expireTimeSeconds / 60;

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Verify Your Email - SmartATS</title>
                <link rel="preconnect" href="https://fonts.googleapis.com">
                <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
                <link href="https://fonts.googleapis.com/css2?family=Open+Sans:wght@400;500;600&family=Poppins:wght@500;600;700&display=swap" rel="stylesheet">
                <style>
                    /* ========== Reset & Base ========== */
                    *, *::before, *::after {
                        margin: 0;
                        padding: 0;
                        box-sizing: border-box;
                    }

                    body {
                        font-family: 'Open Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                        line-height: 1.7;
                        color: #0C4A6E;
                        background: linear-gradient(135deg, #0369A1 0%%, #0EA5E9 50%%, #22C55E 100%%);
                        padding: 24px;
                        min-height: 100vh;
                        -webkit-font-smoothing: antialiased;
                        -moz-osx-font-smoothing: grayscale;
                    }

                    /* ========== Background Animation ========== */
                    body::before {
                        content: '';
                        position: fixed;
                        top: 0;
                        left: 0;
                        right: 0;
                        bottom: 0;
                        background-image:
                            radial-gradient(circle at 20%% 30%%, rgba(255, 255, 255, 0.1) 0%%, transparent 50%%),
                            radial-gradient(circle at 80%% 70%%, rgba(34, 197, 94, 0.15) 0%%, transparent 50%%),
                            radial-gradient(circle at 40%% 80%%, rgba(14, 165, 233, 0.1) 0%%, transparent 50%%);
                        animation: float 20s ease-in-out infinite;
                        pointer-events: none;
                    }

                    @keyframes float {
                        0%%, 100%% { transform: translate(0, 0) scale(1); }
                        33%% { transform: translate(30px, -30px) scale(1.1); }
                        66%% { transform: translate(-20px, 20px) scale(0.9); }
                    }

                    /* ========== Main Container (Glassmorphism) ========== */
                    .email-wrapper {
                        max-width: 620px;
                        margin: 0 auto;
                        position: relative;
                        z-index: 1;
                    }

                    .email-container {
                        background: rgba(255, 255, 255, 0.92);
                        backdrop-filter: blur(20px);
                        -webkit-backdrop-filter: blur(20px);
                        border-radius: 24px;
                        border: 1px solid rgba(255, 255, 255, 0.3);
                        box-shadow:
                            0 8px 32px rgba(3, 105, 161, 0.2),
                            0 2px 8px rgba(0, 0, 0, 0.1),
                            inset 0 1px 0 rgba(255, 255, 255, 0.5);
                        overflow: hidden;
                    }

                    /* ========== Header Section ========== */
                    .header {
                        background: linear-gradient(135deg, #0369A1 0%%, #0EA5E9 100%%);
                        padding: 48px 40px;
                        text-align: center;
                        position: relative;
                        overflow: hidden;
                    }

                    /* Animated pattern overlay */
                    .header::before {
                        content: '';
                        position: absolute;
                        top: -50%%;
                        left: -50%%;
                        width: 200%%;
                        height: 200%%;
                        background:
                            radial-gradient(circle at center, transparent 0%%, transparent 48%%, rgba(255,255,255,0.03) 48%%, rgba(255,255,255,0.03) 50%%);
                        background-size: 24px 24px;
                        animation: pattern-move 30s linear infinite;
                    }

                    @keyframes pattern-move {
                        0%% { transform: translate(0, 0) rotate(0deg); }
                        100%% { transform: translate(24px, 24px) rotate(360deg); }
                    }

                    /* Light reflection effect */
                    .header::after {
                        content: '';
                        position: absolute;
                        top: -50%%;
                        right: -30%%;
                        width: 200%%;
                        height: 200%%;
                        background: radial-gradient(circle, rgba(255,255,255,0.15) 0%%, transparent 60%%);
                        transform: rotate(30deg);
                        pointer-events: none;
                    }

                    .logo {
                        font-family: 'Poppins', sans-serif;
                        font-size: 42px;
                        font-weight: 700;
                        color: #ffffff;
                        letter-spacing: -0.5px;
                        margin-bottom: 8px;
                        position: relative;
                        z-index: 1;
                        text-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
                    }

                    .tagline {
                        font-family: 'Open Sans', sans-serif;
                        color: rgba(255, 255, 255, 0.95);
                        font-size: 15px;
                        font-weight: 500;
                        letter-spacing: 0.5px;
                        position: relative;
                        z-index: 1;
                    }

                    /* ========== Content Section ========== */
                    .content {
                        padding: 48px 40px;
                    }

                    .greeting {
                        font-family: 'Poppins', sans-serif;
                        font-size: 26px;
                        font-weight: 600;
                        color: #0C4A6E;
                        margin-bottom: 16px;
                    }

                    .message {
                        font-size: 16px;
                        color: #475569;
                        line-height: 1.8;
                        margin-bottom: 32px;
                    }

                    /* ========== Verification Code Card ========== */
                    .code-card {
                        background: linear-gradient(135deg, rgba(3, 105, 161, 0.05) 0%%, rgba(14, 165, 233, 0.08) 100%%);
                        border-radius: 16px;
                        padding: 40px;
                        margin: 32px 0;
                        text-align: center;
                        position: relative;
                        border: 2px solid rgba(3, 105, 161, 0.15);
                        box-shadow:
                            0 4px 16px rgba(3, 105, 161, 0.08),
                            inset 0 1px 0 rgba(255, 255, 255, 0.8);
                        overflow: hidden;
                    }

                    /* Animated top border */
                    .code-card::before {
                        content: '';
                        position: absolute;
                        top: 0;
                        left: 0;
                        right: 0;
                        height: 3px;
                        background: linear-gradient(90deg, #0369A1, #0EA5E9, #22C55E, #0EA5E9, #0369A1);
                        background-size: 200%% 100%%;
                        animation: shimmer 3s linear infinite;
                    }

                    @keyframes shimmer {
                        0%% { background-position: 200%% 0; }
                        100%% { background-position: -200%% 0; }
                    }

                    /* Subtle grid pattern */
                    .code-card::after {
                        content: '';
                        position: absolute;
                        top: 0;
                        left: 0;
                        right: 0;
                        bottom: 0;
                        background-image:
                            linear-gradient(rgba(3, 105, 161, 0.03) 1px, transparent 1px),
                            linear-gradient(90deg, rgba(3, 105, 161, 0.03) 1px, transparent 1px);
                        background-size: 20px 20px;
                        pointer-events: none;
                    }

                    .code-label {
                        font-family: 'Open Sans', sans-serif;
                        font-size: 13px;
                        font-weight: 600;
                        color: #0369A1;
                        text-transform: uppercase;
                        letter-spacing: 2.5px;
                        margin-bottom: 24px;
                        position: relative;
                        z-index: 1;
                    }

                    .verification-code {
                        font-family: 'Poppins', sans-serif;
                        font-size: 48px;
                        font-weight: 700;
                        color: #0369A1;
                        letter-spacing: 14px;
                        margin: 24px 0;
                        text-shadow: 0 2px 8px rgba(3, 105, 161, 0.15);
                        position: relative;
                        z-index: 1;
                        user-select: all;
                    }

                    .code-expire {
                        font-size: 14px;
                        color: #64748B;
                        margin-top: 20px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        gap: 6px;
                        position: relative;
                        z-index: 1;
                    }

                    .code-expire strong {
                        color: #0369A1;
                        font-weight: 600;
                    }

                    /* ========== Alert Box ========== */
                    .alert-box {
                        background: linear-gradient(135deg, rgba(34, 197, 94, 0.08) 0%%, rgba(14, 165, 233, 0.06) 100%%);
                        border-left: 4px solid #22C55E;
                        padding: 20px 24px;
                        margin: 28px 0;
                        border-radius: 12px;
                        font-size: 14px;
                        color: #475569;
                        line-height: 1.7;
                        box-shadow: 0 2px 8px rgba(34, 197, 94, 0.1);
                    }

                    .alert-box strong {
                        color: #0369A1;
                        font-weight: 600;
                    }

                    /* ========== Security Notice ========== */
                    .security-notice {
                        font-size: 14px;
                        color: #64748B;
                        text-align: center;
                        margin: 32px 0;
                        padding: 20px;
                        background: rgba(248, 250, 252, 0.8);
                        border-radius: 12px;
                        border: 1px dashed rgba(148, 163, 184, 0.4);
                    }

                    /* ========== Divider ========== */
                    .divider {
                        height: 1px;
                        background: linear-gradient(90deg, transparent, rgba(148, 163, 184, 0.3), transparent);
                        margin: 32px 0;
                    }

                    /* ========== Footer ========== */
                    .footer {
                        background: linear-gradient(180deg, rgba(3, 105, 161, 0.03) 0%%, rgba(14, 165, 233, 0.05) 100%%);
                        padding: 32px 40px;
                        text-align: center;
                        border-top: 1px solid rgba(148, 163, 184, 0.2);
                    }

                    .footer-text {
                        font-size: 13px;
                        color: #64748B;
                        line-height: 1.8;
                        margin: 8px 0;
                    }

                    .footer-links {
                        margin: 20px 0;
                        padding: 20px 0;
                        border-top: 1px solid rgba(148, 163, 184, 0.2);
                        border-bottom: 1px solid rgba(148, 163, 184, 0.2);
                    }

                    .footer-link {
                        color: #0369A1;
                        text-decoration: none;
                        font-size: 13px;
                        font-weight: 500;
                        margin: 0 12px;
                        transition: color 200ms ease;
                    }

                    .footer-link:hover {
                        color: #0EA5E9;
                        text-decoration: underline;
                    }

                    .copyright {
                        font-size: 12px;
                        color: #94A3B8;
                        margin-top: 16px;
                    }

                    /* ========== Responsive Design ========== */
                    @media only screen and (max-width: 640px) {
                        body {
                            padding: 16px;
                        }

                        .header, .content, .footer {
                            padding: 32px 24px;
                        }

                        .logo {
                            font-size: 36px;
                        }

                        .greeting {
                            font-size: 22px;
                        }

                        .verification-code {
                            font-size: 36px;
                            letter-spacing: 10px;
                        }

                        .code-card {
                            padding: 28px 20px;
                        }

                        .footer-link {
                            display: block;
                            margin: 8px 0;
                        }
                    }

                    /* ========== Accessibility: Reduced Motion ========== */
                    @media (prefers-reduced-motion: reduce) {
                        *, *::before, *::after {
                            animation-duration: 0.01ms !important;
                            animation-iteration-count: 1 !important;
                            transition-duration: 0.01ms !important;
                        }
                    }
                </style>
            </head>
            <body>
                <div class="email-wrapper">
                    <div class="email-container">
                        <!-- Header -->
                        <div class="header">
                            <div class="logo">SmartATS</div>
                            <div class="tagline">Intelligent Recruitment Platform</div>
                        </div>

                        <!-- Content -->
                        <div class="content">
                            <div class="greeting">Hello,</div>

                            <div class="message">
                                Thank you for choosing SmartATS! You're just one step away from accessing our intelligent recruitment platform. Please use the verification code below to complete your registration.
                            </div>

                            <!-- Verification Code Card -->
                            <div class="code-card">
                                <div class="code-label">Verification Code</div>
                                <div class="verification-code">%s</div>
                                <div class="code-expire">
                                    Valid for <strong>%d minutes</strong>
                                </div>
                            </div>

                            <!-- Alert Box -->
                            <div class="alert-box">
                                <strong>Tip:</strong> Please enter this code on the registration page to complete your signup. Keep this code secure and do not share it with anyone.
                            </div>

                            <!-- Security Notice -->
                            <div class="security-notice">
                                For your security, this code will expire after use. If you didn't request this code, please ignore this email or contact our support team.
                            </div>
                        </div>

                        <!-- Divider -->
                        <div class="divider"></div>

                        <!-- Footer -->
                        <div class="footer">
                            <p class="footer-text">
                                This is an automated email from SmartATS.<br>
                                Please do not reply directly to this message.
                            </p>

                            <div class="footer-links">
                                <a href="https://smartats.com/privacy" class="footer-link">Privacy Policy</a>
                                <a href="https://smartats.com/terms" class="footer-link">Terms of Service</a>
                                <a href="https://smartats.com/support" class="footer-link">Contact Support</a>
                            </div>

                            <p class="copyright">
                                &copy; 2026 SmartATS. All rights reserved.<br>
                                Building the future of intelligent recruitment.
                            </p>
                        </div>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(code, expireMinutes);
    }
}

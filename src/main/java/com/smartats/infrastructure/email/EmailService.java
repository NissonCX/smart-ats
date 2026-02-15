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
     * 构建邮件内容
     *
     * @param code 验证码
     * @return HTML 格式的邮件内容
     */
    private String buildEmailContent(String code) {
        int expireMinutes = expireTimeSeconds / 60;

        return """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <style>
                                    body { font-family: Arial, sans-serif; line-height: 1.6;
                color: #333; }
                                    .container { max-width: 600px; margin: 0 auto; padding:
                20px; }
                                    .header { background: linear-gradient(135deg, #667eea 0%%,
                 #764ba2 100%%); color: white; padding: 30px; text-align: center;
                border-radius: 10px 10px 0 0; }
                                    .content { background: #f9f9f9; padding: 30px;
                border-radius: 0 0 10px 10px; }
                                    .code { font-size: 32px; font-weight: bold; color:
                #667eea; text-align: center; padding: 20px; background: white; border-radius:
                5px; margin: 20px 0; }
                                    .footer { text-align: center; color: #999; font-size:
                12px; margin-top: 20px; }
                                </style>
                            </head>
                            <body>
                                <div class="container">
                                    <div class="header">
                                        <h1>SmartATS 邮箱验证</h1>
                                    </div>
                                    <div class="content">
                                        <p>您好，</p>
                                        <p>您正在注册 SmartATS
                智能招聘系统，您的验证码是：</p>
                                        <div class="code">%s</div>
                                        <p>验证码有效期为 <strong>%d
                分钟</strong>，请尽快完成注册。</p>
                                        <p>如果这不是您的操作，请忽略此邮件。</p>
                                    </div>
                                    <div class="footer">
                                        <p>此邮件由系统自动发送，请勿回复。</p>
                                        <p>&copy; 2026 SmartATS. All rights reserved.</p>
                                    </div>
                                </div>
                            </body>
                            </html>
                """.formatted(code, expireMinutes);
    }
}

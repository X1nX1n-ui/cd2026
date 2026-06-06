package com.cd.server;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailSenderService {

    private static final Logger log = LoggerFactory.getLogger(EmailSenderService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.from}")
    private String fromAddress;

    public EmailSenderService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendVerificationCode(String to, String code, int expireMinutes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject("\u5b89\u5168\u9a8c\u8bc1\u7801 - \u5a01\u80c1\u6001\u52bf\u611f\u77e5\u5e73\u53f0");

            String htmlContent = buildVerificationEmailHtml(code, expireMinutes);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("\u9a8c\u8bc1\u7801\u90ae\u4ef6\u5df2\u53d1\u9001: {}", to);
        } catch (MessagingException e) {
            log.error("\u90ae\u4ef6\u53d1\u9001\u5931\u8d25, to={}, error={}", to, e.getMessage(), e);
            throw new RuntimeException("\u90ae\u4ef6\u53d1\u9001\u5931\u8d25: " + e.getMessage(), e);
        }
    }

    private String buildVerificationEmailHtml(String code, int expireMinutes) {
        return "<div style=\"max-width:480px;margin:0 auto;padding:32px 24px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:#f8fafc;border-radius:12px;\">"
            + "<div style=\"background:#fff;padding:32px 24px;border-radius:10px;box-shadow:0 2px 8px rgba(0,0,0,0.06);\">"
            + "<h2 style=\"margin:0 0 8px;font-size:20px;color:#1f2937;\">\u5b89\u5168\u9a8c\u8bc1\u7801</h2>"
            + "<p style=\"margin:0 0 24px;font-size:14px;color:#6b7280;line-height:1.6;\">\u60a8\u6b63\u5728\u8fdb\u884c\u5b89\u5168\u9a8c\u8bc1\uff0c\u8bf7\u4f7f\u7528\u4ee5\u4e0b\u9a8c\u8bc1\u7801\u5b8c\u6210\u64cd\u4f5c\uff1a</p>"
            + "<div style=\"background:#eff6ff;border:1px solid #bfdbfe;border-radius:8px;padding:16px;text-align:center;margin-bottom:24px;\">"
            + "<span style=\"font-size:32px;font-weight:700;color:#4f86f7;letter-spacing:8px;\">" + code + "</span>"
            + "</div>"
            + "<p style=\"margin:0 0 8px;font-size:13px;color:#9ca3af;\">\u9a8c\u8bc1\u7801\u5728 <strong>" + expireMinutes + " \u5206\u949f</strong>\u5185\u6709\u6548\uff0c\u8bf7\u52ff\u900f\u9732\u7ed9\u4ed6\u4eba\u3002</p>"
            + "<p style=\"margin:0;font-size:13px;color:#9ca3af;\">\u5982\u975e\u60a8\u672c\u4eba\u64cd\u4f5c\uff0c\u8bf7\u5ffd\u7565\u6b64\u90ae\u4ef6\u3002</p>"
            + "</div>"
            + "<p style=\"margin:16px 0 0;font-size:12px;color:#d1d5db;text-align:center;\">\u5a01\u80c1\u6001\u52bf\u611f\u77e5\u5e73\u53f0 \u00b7 \u5b89\u5168\u9a8c\u8bc1\u90ae\u4ef6</p>"
            + "</div>";
    }
}
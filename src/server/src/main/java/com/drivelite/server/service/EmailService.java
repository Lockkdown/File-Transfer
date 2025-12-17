package com.drivelite.server.service;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.io.File;
import java.security.SecureRandom;
import java.util.Properties;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Service để gửi email OTP.
 * Sử dụng SMTP (Gmail, Outlook, hoặc SMTP server khác).
 */
public class EmailService {

    private static EmailService instance;

    private final String smtpHost;
    private final int smtpPort;
    private final String smtpUsername;
    private final String smtpPassword;
    private final String fromEmail;
    private final String fromName;
    private final boolean enabled;

    private final SecureRandom random = new SecureRandom();

    private EmailService() {
        Dotenv dotenv = Dotenv.configure()
                .directory(findEnvDirectory())
                .ignoreIfMissing()
                .load();

        this.smtpHost = dotenv.get("SMTP_HOST", "smtp.gmail.com");
        this.smtpPort = Integer.parseInt(dotenv.get("SMTP_PORT", "587"));
        this.smtpUsername = dotenv.get("SMTP_USERNAME", "");
        this.smtpPassword = dotenv.get("SMTP_PASSWORD", "");
        this.fromEmail = dotenv.get("SMTP_FROM_EMAIL", smtpUsername);
        this.fromName = dotenv.get("SMTP_FROM_NAME", "Drive-lite");
        this.enabled = Boolean.parseBoolean(dotenv.get("SMTP_ENABLED", "false"));

        if (enabled) {
            System.out.println("[EMAIL] Service enabled: " + smtpHost + ":" + smtpPort);
        } else {
            System.out.println("[EMAIL] Service DISABLED - OTP will be logged to console");
        }
    }

    public static synchronized EmailService getInstance() {
        if (instance == null) {
            instance = new EmailService();
        }
        return instance;
    }

    /**
     * Tạo OTP 6 số ngẫu nhiên.
     */
    public String generateOTP() {
        int otp = 100000 + random.nextInt(900000); // 100000 - 999999
        return String.valueOf(otp);
    }

    /**
     * Gửi OTP qua email.
     * 
     * @param toEmail Email người nhận
     * @param otp Mã OTP
     * @return true nếu gửi thành công
     */
    public boolean sendOTP(String toEmail, String otp) {
        String subject = "Drive-lite - Mã xác nhận đặt lại mật khẩu";
        String body = buildOTPEmailBody(otp);

        return sendEmail(toEmail, subject, body);
    }

    /**
     * Gửi email.
     * Nếu SMTP_ENABLED = false, chỉ log ra console (cho development).
     */
    public boolean sendEmail(String toEmail, String subject, String body) {
        if (!enabled) {
            // Development mode - chỉ log
            System.out.println("========================================");
            System.out.println("[EMAIL - DEV MODE] To: " + toEmail);
            System.out.println("[EMAIL - DEV MODE] Subject: " + subject);
            System.out.println("[EMAIL - DEV MODE] Body:");
            System.out.println(body);
            System.out.println("========================================");
            return true;
        }

        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", smtpHost);
            props.put("mail.smtp.port", String.valueOf(smtpPort));
            props.put("mail.smtp.ssl.trust", smtpHost);

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(smtpUsername, smtpPassword);
                }
            });

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromEmail, fromName));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject(subject);
            message.setContent(body, "text/html; charset=utf-8");

            Transport.send(message);

            System.out.println("[EMAIL] Sent to: " + toEmail);
            return true;

        } catch (Exception e) {
            System.err.println("[EMAIL] Failed to send email to " + toEmail + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Tạo nội dung email OTP (HTML).
     */
    private String buildOTPEmailBody(String otp) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: #4F46E5; color: white; padding: 20px; text-align: center; border-radius: 8px 8px 0 0; }
                    .content { background: #f9f9f9; padding: 30px; border-radius: 0 0 8px 8px; }
                    .otp-code { font-size: 32px; font-weight: bold; color: #4F46E5; text-align: center; 
                               padding: 20px; background: white; border-radius: 8px; margin: 20px 0;
                               letter-spacing: 8px; }
                    .warning { color: #dc2626; font-size: 14px; margin-top: 20px; }
                    .footer { text-align: center; color: #666; font-size: 12px; margin-top: 20px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🔐 Drive-lite</h1>
                    </div>
                    <div class="content">
                        <p>Xin chào,</p>
                        <p>Bạn đã yêu cầu đặt lại mật khẩu cho tài khoản Drive-lite của mình.</p>
                        <p>Mã xác nhận (OTP) của bạn là:</p>
                        <div class="otp-code">%s</div>
                        <p class="warning">⚠️ Mã này sẽ hết hạn sau <strong>15 phút</strong>.</p>
                        <p class="warning">⚠️ Không chia sẻ mã này với bất kỳ ai.</p>
                        <p>Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này.</p>
                    </div>
                    <div class="footer">
                        <p>© 2024 Drive-lite. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(otp);
    }

    private static String findEnvDirectory() {
        String[] possiblePaths = { ".", "..", "../..", "../../..", System.getProperty("user.dir") };
        for (String path : possiblePaths) {
            File envFile = new File(path, ".env");
            if (envFile.exists()) {
                return path;
            }
        }
        return ".";
    }

    public boolean isEnabled() {
        return enabled;
    }
}

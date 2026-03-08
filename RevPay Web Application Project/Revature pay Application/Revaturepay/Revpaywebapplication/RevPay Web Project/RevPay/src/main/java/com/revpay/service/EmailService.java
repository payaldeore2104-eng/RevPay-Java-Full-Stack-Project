package com.revpay.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.mail.internet.MimeMessage;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@revpay.com}")
    private String fromEmail;

    /**
     * Sends a 6-digit OTP to the user's email for 2FA login verification.
     * Falls back to console logging if SMTP is not configured.
     */
    public void sendOtp(String toEmail, String otp) {
        if (mailSender == null) {
            logger.warn("[RevPay OTP] Mail sender not configured. OTP for {}: {}", toEmail, otp);
            System.out.println("[RevPay OTP] (Email not configured) OTP for " + toEmail + " : " + otp);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("RevPay – Your Login OTP Code");
            helper.setText(buildOtpEmailHtml(otp), true); // true = HTML

            mailSender.send(message);
            logger.info("[RevPay OTP] OTP email sent successfully to {}", toEmail);

        } catch (Exception e) {
            // Log the error but fall back to console print so login still works
            logger.error("[RevPay OTP] Failed to send OTP email to {}: {}", toEmail, e.getMessage());
            System.out.println("[RevPay OTP] (Email send failed) OTP for " + toEmail + " : " + otp);
        }
    }

    /**
     * Sends a welcome email after successful registration.
     */
    public void sendWelcomeEmail(String toEmail, String fullName) {
        if (mailSender == null)
            return;
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Welcome to RevPay!");
            helper.setText(buildWelcomeEmailHtml(fullName), true);
            mailSender.send(message);
            logger.info("[RevPay] Welcome email sent to {}", toEmail);
        } catch (Exception e) {
            logger.warn("[RevPay] Welcome email failed for {}: {}", toEmail, e.getMessage());
        }
    }

    // ── HTML Email Templates ───────────────────────────────────────────────

    private String buildOtpEmailHtml(String otp) {
        return "<!DOCTYPE html>" +
                "<html><head><meta charset='UTF-8'></head><body style='margin:0;padding:0;font-family:Segoe UI,sans-serif;background:#f0f2f5;'>"
                +
                "<div style='max-width:480px;margin:40px auto;background:#fff;border-radius:14px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.1);'>"
                +
                "  <div style='background:linear-gradient(135deg,#0056b3,#003d82);padding:28px 30px;text-align:center;'>"
                +
                "    <h1 style='color:#fff;margin:0;font-size:26px;letter-spacing:1px;'>RevPay</h1>" +
                "    <p style='color:rgba(255,255,255,0.8);margin:6px 0 0;font-size:13px;'>Secure Digital Payments</p>"
                +
                "  </div>" +
                "  <div style='padding:36px 30px;text-align:center;'>" +
                "    <p style='font-size:16px;color:#333;margin:0 0 10px;'>Your Login Verification Code</p>" +
                "    <div style='display:inline-block;background:#f0f4ff;border:2px dashed #0056b3;border-radius:12px;padding:18px 40px;margin:16px 0;'>"
                +
                "      <span style='font-size:38px;font-weight:700;letter-spacing:10px;color:#0056b3;'>" + otp
                + "</span>" +
                "    </div>" +
                "    <p style='color:#666;font-size:14px;margin:16px 0 0;'>This OTP is valid for <strong>5 minutes</strong>.</p>"
                +
                "    <p style='color:#999;font-size:12px;margin:8px 0 0;'>If you did not request this, please ignore this email.</p>"
                +
                "  </div>" +
                "  <div style='background:#f8f9fa;padding:16px 30px;text-align:center;border-top:1px solid #eee;'>" +
                "    <p style='color:#aaa;font-size:11px;margin:0;'>© 2026 RevPay | support@revpay.com</p>" +
                "  </div>" +
                "</div></body></html>";
    }

    private String buildWelcomeEmailHtml(String fullName) {
        return "<!DOCTYPE html>" +
                "<html><head><meta charset='UTF-8'></head><body style='margin:0;padding:0;font-family:Segoe UI,sans-serif;background:#f0f2f5;'>"
                +
                "<div style='max-width:480px;margin:40px auto;background:#fff;border-radius:14px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.1);'>"
                +
                "  <div style='background:linear-gradient(135deg,#0056b3,#003d82);padding:28px 30px;text-align:center;'>"
                +
                "    <h1 style='color:#fff;margin:0;font-size:26px;'>RevPay</h1>" +
                "  </div>" +
                "  <div style='padding:36px 30px;'>" +
                "    <h2 style='color:#1a1a2e;margin:0 0 12px;'>Welcome, " + fullName + "! 🎉</h2>" +
                "    <p style='color:#555;font-size:14px;line-height:1.6;'>Your RevPay account has been created successfully.</p>"
                +
                "    <p style='color:#555;font-size:14px;line-height:1.6;'>You can now send money, manage your wallet, and much more.</p>"
                +
                "    <a href='http://localhost:8080/login' style='display:inline-block;margin-top:20px;background:#0056b3;color:#fff;padding:12px 28px;border-radius:8px;text-decoration:none;font-weight:700;'>Login to RevPay</a>"
                +
                "  </div>" +
                "  <div style='background:#f8f9fa;padding:16px 30px;text-align:center;border-top:1px solid #eee;'>" +
                "    <p style='color:#aaa;font-size:11px;margin:0;'>© 2026 RevPay | support@revpay.com</p>" +
                "  </div>" +
                "</div></body></html>";
    }
}

package com.duriancare.notification.service;

import com.duriancare.notification.config.MailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        name = "duriancare.notification.mail.enabled",
        havingValue = "true")
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final MailProperties properties;
    private final NotificationHistoryService historyService;

    public SmtpEmailService(
            JavaMailSender mailSender,
            MailProperties properties,
            NotificationHistoryService historyService) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.historyService = historyService;
    }

    @Override
    public void sendOtpEmail(String recipient, String otp, long ttlMinutes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.fromAddress(), properties.fromName());
            helper.setTo(recipient);
            helper.setSubject("DurianCare - Mã xác nhận đăng nhập");
            helper.setText(buildPlainTextBody(otp, ttlMinutes), buildHtmlBody(otp, ttlMinutes));
            mailSender.send(message);
            historyService.recordOtpSent(recipient);
        } catch (MessagingException | UnsupportedEncodingException | RuntimeException exception) {
            historyService.recordOtpFailure(recipient, exception.getMessage());
            throw new EmailDeliveryException("Unable to deliver OTP email", exception);
        }
    }

    private String buildPlainTextBody(String otp, long ttlMinutes) {
        return """
                DurianCare - Mã xác nhận đăng nhập

                Xin chào,

                Đây là mã OTP để xác nhận tài khoản DurianCare của bạn:

                %s

                Mã này có hiệu lực trong %d phút.

                Nếu bạn không yêu cầu mã này, vui lòng bỏ qua email này.

                Trân trọng,
                Đội ngũ DurianCare
                """
                .formatted(otp, ttlMinutes);
    }

    private String buildHtmlBody(String otp, long ttlMinutes) {
        String currentYear = String.valueOf(Year.now().getValue());
        return """
                <!doctype html>
                <html lang="vi">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>DurianCare OTP</title>
                </head>
                <body style="margin:0;padding:0;background-color:#f4f7fb;font-family:'Segoe UI',Tahoma,Arial,'Helvetica Neue',sans-serif;color:#1f2937;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background-color:#f4f7fb;padding:32px 12px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:640px;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 10px 30px rgba(15, 23, 42, 0.08);">
                          <tr>
                            <td style="background:linear-gradient(135deg,#15803d 0%%,#22c55e 100%%);padding:28px 32px;color:#ffffff;">
                              <div style="font-size:13px;letter-spacing:1.2px;text-transform:uppercase;opacity:0.95;">DurianCare SmartFarm</div>
                              <div style="font-size:28px;font-weight:700;line-height:1.2;margin-top:8px;">Mã xác nhận đăng nhập</div>
                              <div style="font-size:15px;line-height:1.6;margin-top:10px;max-width:520px;">
                                Hoàn thành xác thực tài khoản và tiếp tục truy cập hệ sinh thái quản lý sầu riêng thông minh.
                              </div>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:32px;">
                              <div style="font-size:16px;line-height:1.7;margin-bottom:20px;">
                                Xin chào,
                                <br><br>
                                Chúng tôi đã nhận được yêu cầu xác nhận tài khoản của bạn. Sử dụng mã OTP bên dưới để hoàn tất đăng nhập hoặc xác thực:
                              </div>
                              <div style="text-align:center;margin:28px 0;">
                                <div style="display:inline-block;background:#ecfdf5;border:1px solid #86efac;border-radius:14px;padding:18px 28px;font-size:34px;font-weight:800;letter-spacing:6px;color:#166534;min-width:220px;">
                                  %s
                                </div>
                              </div>
                              <div style="background:#f8fafc;border:1px solid #e2e8f0;border-radius:12px;padding:18px 20px;margin:24px 0;">
                                <div style="font-size:14px;line-height:1.7;color:#334155;">
                                  <strong>Hiệu lực:</strong> %d phút<br>
                                  <strong>Bảo mật:</strong> Tuyệt đối không chia sẻ mã này với bất kỳ ai<br>
                                  <strong>Lưu ý:</strong> Nếu bạn không yêu cầu thao tác này, có thể bỏ qua email
                                </div>
                              </div>
                              <div style="font-size:14px;line-height:1.8;color:#475569;">
                                Email này được gửi từ hệ thống DurianCare để bảo vệ tài khoản của bạn.
                                Nếu cần hỗ trợ, vui lòng liên hệ đội ngũ kỹ thuật hoặc kỹ sư nông nghiệp phụ trách.
                              </div>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:0 32px 28px 32px;">
                              <div style="border-top:1px solid #e2e8f0;padding-top:18px;font-size:12px;line-height:1.7;color:#94a3b8;text-align:center;">
                                <div style="margin-bottom:4px;">DurianCare SmartFarm - Hệ sinh thái quản lý và phát hiện bệnh sầu riêng</div>
                                <div>&copy; %s DurianCare. All rights reserved.</div>
                              </div>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """
                .formatted(otp, ttlMinutes, currentYear);
    }
}

package face_recognition.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationMailService {

    private final JavaMailSender mailSender;

    @Async
    public void sendWarningEmailAsync(String toEmail, String fullName, String content) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject("⚠️ Cảnh báo nghỉ học - FaceAttend");
            helper.setText(buildWarningEmailHtml(fullName, content), true);
            mailSender.send(mime);
        } catch (Exception e) {
            log.error("Gửi email cảnh báo thất bại cho {}", toEmail, e);
        }
    }

    private String buildWarningEmailHtml(String fullName, String content) {
        return """
            <div style="font-family:'Segoe UI',Arial,sans-serif;max-width:480px;margin:0 auto;
                        background:#f4f7fb;padding:32px 0;">
              <div style="background:#ffffff;border-radius:16px;overflow:hidden;
                          box-shadow:0 4px 24px rgba(20,40,80,0.08);">
                <div style="background:linear-gradient(135deg,#ff8a3d,#e65100);
                            padding:28px 32px;text-align:center;">
                  <div style="font-size:20px;font-weight:700;color:#fff;letter-spacing:.5px;">
                    FaceAttend
                  </div>
                  <div style="font-size:13px;color:#ffe4cf;margin-top:4px;">
                    Cảnh báo nghỉ học
                  </div>
                </div>
                <div style="padding:32px;">
                  <p style="font-size:15px;color:#2b3648;margin:0 0 8px;">
                    Xin chào <strong>%s</strong>,
                  </p>
                  <p style="font-size:14px;color:#5b6b82;line-height:1.6;margin:0;">
                    %s
                  </p>
                </div>
                <div style="background:#f4f7fb;padding:16px 32px;text-align:center;">
                  <span style="font-size:11px;color:#a3adbd;">
                    © FaceAttend · Hệ thống điểm danh bằng khuôn mặt
                  </span>
                </div>
              </div>
            </div>
            """.formatted(fullName, content);
    }
}
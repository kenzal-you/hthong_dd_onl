package face_recognition.service;

import face_recognition.model.Subject;
import face_recognition.repository.SubjectRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubjectLinkService {

    private final SubjectRepository subjectRepository;
    private final JavaMailSender mailSender;

    private final Map<Long, CodeEntry> codeStore = new ConcurrentHashMap<>();
    private final Map<String, TokenEntry> tokenStore = new ConcurrentHashMap<>();

    private final Map<String, CodeEntry> emailCodeStore = new ConcurrentHashMap<>();
    private final Map<String, EmailTokenEntry> emailTokenStore = new ConcurrentHashMap<>();

    private static final long CODE_TTL_MS = 5 * 60 * 1000;
    private static final long TOKEN_TTL_MS = 15 * 60 * 1000;

    private record CodeEntry(String code, long expiresAt) {}
    private record TokenEntry(Long subjectId, long expiresAt) {}
    private record EmailTokenEntry(String email, long expiresAt) {}

    public List<Map<String, Object>> getUnlinkedSubjects(String search) {
        String s = search == null ? "" : search.trim().toLowerCase();
        return subjectRepository.findByLinkedUserIsNullAndIsDeletedFalse().stream()
                .filter(sub -> s.isEmpty()
                        || (sub.getFullName() != null && sub.getFullName().toLowerCase().contains(s))
                        || (sub.getStudentCode() != null && sub.getStudentCode().toLowerCase().contains(s)))
                .map(sub -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", sub.getId());
                    m.put("fullName", sub.getFullName());
                    m.put("studentCode", sub.getStudentCode());
                    m.put("email", maskEmail(sub.getEmail()));
                    m.put("className", sub.getClassRoom() != null ? sub.getClassRoom().getName() : null);
                    return m;
                })
                .toList();
    }

    public void sendCode(Long subjectId) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sinh viên!"));

        if (subject.getLinkedUser() != null)
            throw new RuntimeException("Sinh viên này đã được liên kết với tài khoản khác!");
        if (subject.getEmail() == null || subject.getEmail().isBlank())
            throw new RuntimeException("Sinh viên này chưa có email, vui lòng liên hệ quản trị viên!");

        String code = String.valueOf(100000 + new SecureRandom().nextInt(900000));
        codeStore.put(subjectId, new CodeEntry(code, System.currentTimeMillis() + CODE_TTL_MS));
        sendCodeMailAsync(subject.getEmail(), subject.getFullName(), code);
    }

    public String verifyCode(Long subjectId, String code) {
        CodeEntry entry = codeStore.get(subjectId);
        if (entry == null || entry.expiresAt() < System.currentTimeMillis())
            throw new RuntimeException("Mã xác nhận đã hết hạn, vui lòng gửi lại!");
        if (!entry.code().equals(code))
            throw new RuntimeException("Mã xác nhận không đúng!");

        codeStore.remove(subjectId);

        String token = UUID.randomUUID().toString();
        tokenStore.put(token, new TokenEntry(subjectId, System.currentTimeMillis() + TOKEN_TTL_MS));
        return token;
    }

    public Long consumeToken(String token, Long expectedSubjectId) {
        if (token == null)
            throw new RuntimeException("Chưa xác thực mã liên kết!");
        TokenEntry entry = tokenStore.get(token);
        if (entry == null || entry.expiresAt() < System.currentTimeMillis())
            throw new RuntimeException("Phiên xác nhận đã hết hạn, vui lòng xác nhận lại mã!");
        if (!entry.subjectId().equals(expectedSubjectId))
            throw new RuntimeException("Thông tin xác nhận không khớp!");

        tokenStore.remove(token);
        return entry.subjectId();
    }

    public void sendCodeToEmail(String email, String fullName) {
        if (email == null || email.isBlank())
            throw new RuntimeException("Email không được để trống!");
        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$"))
            throw new RuntimeException("Email không hợp lệ!");

        String code = String.valueOf(100000 + new SecureRandom().nextInt(900000));
        emailCodeStore.put(email.toLowerCase(), new CodeEntry(code, System.currentTimeMillis() + CODE_TTL_MS));
        sendCodeMailAsync(email, (fullName == null || fullName.isBlank()) ? "bạn" : fullName, code);
    }

    public String verifyEmailCode(String email, String code) {
        if (email == null || email.isBlank())
            throw new RuntimeException("Email không hợp lệ!");
        String key = email.toLowerCase();
        CodeEntry entry = emailCodeStore.get(key);
        if (entry == null || entry.expiresAt() < System.currentTimeMillis())
            throw new RuntimeException("Mã xác nhận đã hết hạn, vui lòng gửi lại!");
        if (!entry.code().equals(code))
            throw new RuntimeException("Mã xác nhận không đúng!");

        emailCodeStore.remove(key);

        String token = UUID.randomUUID().toString();
        emailTokenStore.put(token, new EmailTokenEntry(key, System.currentTimeMillis() + TOKEN_TTL_MS));
        return token;
    }

    public void consumeEmailToken(String token, String expectedEmail) {
        if (expectedEmail == null || expectedEmail.isBlank()) return;
        if (token == null)
            throw new RuntimeException("Vui lòng xác nhận mã gửi về email trước!");

        EmailTokenEntry entry = emailTokenStore.get(token);
        if (entry == null || entry.expiresAt() < System.currentTimeMillis())
            throw new RuntimeException("Phiên xác nhận email đã hết hạn, vui lòng xác nhận lại!");
        if (!entry.email().equalsIgnoreCase(expectedEmail))
            throw new RuntimeException("Email xác nhận không khớp với email đã đăng ký!");

        emailTokenStore.remove(token);
    }

    @Async
    public void sendCodeMailAsync(String toEmail, String fullName, String code) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject("Mã xác nhận - FaceAttend");
            helper.setText(buildCodeEmailHtml(fullName, code), true);
            mailSender.send(mime);
        } catch (Exception e) {
            log.error("Gửi email thất bại cho {}", toEmail, e);
        }
    }

    private String buildCodeEmailHtml(String fullName, String code) {
        return """
            <div style="font-family:'Segoe UI',Arial,sans-serif;max-width:480px;margin:0 auto;
                        background:#f4f7fb;padding:32px 0;">
              <div style="background:#ffffff;border-radius:16px;overflow:hidden;
                          box-shadow:0 4px 24px rgba(20,40,80,0.08);">
                <div style="background:linear-gradient(135deg,#4f8ef7,#3057d8);
                            padding:28px 32px;text-align:center;">
                  <div style="font-size:20px;font-weight:700;color:#fff;letter-spacing:.5px;">
                    FaceAttend
                  </div>
                  <div style="font-size:13px;color:#dce6ff;margin-top:4px;">
                    Xác nhận email
                  </div>
                </div>
                <div style="padding:32px;">
                  <p style="font-size:15px;color:#2b3648;margin:0 0 8px;">
                    Xin chào <strong>%s</strong>,
                  </p>
                  <p style="font-size:14px;color:#5b6b82;line-height:1.6;margin:0 0 24px;">
                    Vui lòng nhập mã xác nhận bên dưới để hoàn tất yêu cầu:
                  </p>
                  <div style="background:#f0f4ff;border:1px dashed #4f8ef7;border-radius:12px;
                              text-align:center;padding:18px 0;margin-bottom:24px;">
                    <span style="font-size:32px;font-weight:700;letter-spacing:8px;color:#3057d8;">
                      %s
                    </span>
                  </div>
                  <p style="font-size:13px;color:#8a96a8;margin:0 0 4px;">
                    ⏱ Mã có hiệu lực trong <strong>5 phút</strong>.
                  </p>
                  <p style="font-size:13px;color:#8a96a8;margin:0;">
                    Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email này.
                  </p>
                </div>
                <div style="background:#f4f7fb;padding:16px 32px;text-align:center;">
                  <span style="font-size:11px;color:#a3adbd;">
                    © FaceAttend · Hệ thống điểm danh bằng khuôn mặt
                  </span>
                </div>
              </div>
            </div>
            """.formatted(fullName, code);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@");
        String local = parts[0].length() <= 2 ? parts[0] : parts[0].substring(0, 2) + "***";
        return local + "@" + parts[1];
    }
}
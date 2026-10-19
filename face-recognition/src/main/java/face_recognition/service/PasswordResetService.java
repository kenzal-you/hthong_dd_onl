package face_recognition.service;

import face_recognition.model.User;
import face_recognition.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SubjectLinkService subjectLinkService;

    private final Map<String, CodeEntry> codeStore = new ConcurrentHashMap<>();
    private final Map<String, TokenEntry> tokenStore = new ConcurrentHashMap<>();

    private static final long CODE_TTL_MS = 5 * 60 * 1000;
    private static final long TOKEN_TTL_MS = 15 * 60 * 1000;

    private record CodeEntry(String code, long expiresAt) {}
    private record TokenEntry(String username, long expiresAt) {}

    public String sendResetCode(String username) {
        if (username == null || username.isBlank())
            throw new RuntimeException("Vui lòng nhập tên đăng nhập!");

        User user = userRepository.findByUsername(username.trim())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản với tên đăng nhập này!"));

        if (Boolean.TRUE.equals(user.getIsLocked()))
            throw new RuntimeException("Tài khoản đã bị khóa, vui lòng liên hệ quản trị viên!");
        if (user.getEmail() == null || user.getEmail().isBlank())
            throw new RuntimeException("Tài khoản này chưa có email, không thể khôi phục mật khẩu. Vui lòng liên hệ quản trị viên!");

        String code = String.valueOf(100000 + new SecureRandom().nextInt(900000));
        codeStore.put(username.trim(), new CodeEntry(code, System.currentTimeMillis() + CODE_TTL_MS));

        subjectLinkService.sendCodeMailAsync(user.getEmail(), user.getFullName(), code);

        return maskEmail(user.getEmail());
    }

    public String verifyResetCode(String username, String code) {
        if (username == null || username.isBlank())
            throw new RuntimeException("Vui lòng nhập tên đăng nhập!");

        CodeEntry entry = codeStore.get(username.trim());
        if (entry == null || entry.expiresAt() < System.currentTimeMillis())
            throw new RuntimeException("Mã xác nhận đã hết hạn, vui lòng gửi lại!");
        if (!entry.code().equals(code))
            throw new RuntimeException("Mã xác nhận không đúng!");

        codeStore.remove(username.trim());

        String token = UUID.randomUUID().toString();
        tokenStore.put(token, new TokenEntry(username.trim(), System.currentTimeMillis() + TOKEN_TTL_MS));
        return token;
    }

    @Transactional
    public void resetPassword(String token, String newPassword, String confirmPassword) {
        if (token == null)
            throw new RuntimeException("Vui lòng xác nhận mã trước khi đặt lại mật khẩu!");
        if (newPassword == null || newPassword.length() < 6)
            throw new RuntimeException("Mật khẩu mới phải ít nhất 6 ký tự!");
        if (!newPassword.equals(confirmPassword))
            throw new RuntimeException("Xác nhận mật khẩu không khớp!");

        TokenEntry entry = tokenStore.get(token);
        if (entry == null || entry.expiresAt() < System.currentTimeMillis())
            throw new RuntimeException("Phiên xác nhận đã hết hạn, vui lòng thực hiện lại từ đầu!");

        User user = userRepository.findByUsername(entry.username())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản!"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        tokenStore.remove(token);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@");
        String local = parts[0].length() <= 2 ? parts[0] : parts[0].substring(0, 2) + "***";
        return local + "@" + parts[1];
    }
}
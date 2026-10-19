package face_recognition.controller;
import face_recognition.dto.request.CreateTeacherRequest;
import face_recognition.dto.request.ChangeEmailRequest;
import face_recognition.dto.request.ChangePasswordRequest;
import face_recognition.dto.request.ChangeUsernameRequest;
import face_recognition.dto.request.RegisterRequest;
import face_recognition.dto.response.PagedResponse;
import face_recognition.dto.response.UserResponse;
import face_recognition.model.Subject;
import face_recognition.model.User;
import face_recognition.repository.SubjectRepository;
import face_recognition.repository.UserRepository;
import face_recognition.service.SubjectService;
import face_recognition.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UserController {

    private final UserService userService;
    private final SubjectService subjectService;
    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;


    @GetMapping("/teachers")
    public ResponseEntity<List<UserResponse>> getTeachers() {
        return ResponseEntity.ok(userService.getAllTeachers());
    }

    @PostMapping("/create-teacher")
    public ResponseEntity<?> createTeacher(@RequestBody CreateTeacherRequest body) {
        try {
            UserResponse res = userService.createTeacher(body);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
    @PutMapping("/change-email")
    public ResponseEntity<?> changeEmail(Authentication authentication,
                                         @RequestBody ChangeEmailRequest body) {
        try {
            UserResponse res = userService.changeEmail(authentication.getName(), body.getNewEmail());
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/me/info")
    public ResponseEntity<?> getMyAccountInfo(Authentication authentication) {
        try {
            User user = userRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User không tồn tại!"));
            return ResponseEntity.ok(userService.toDTO(user));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/change-password")
    public ResponseEntity<?> changePassword(Authentication authentication,
                                            @RequestBody ChangePasswordRequest  body) {
        try {
            userService.changePassword(authentication.getName(), body);
            return ResponseEntity.ok(Map.of("message", "Đổi mật khẩu thành công!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
    @PutMapping("/change-username")
    public ResponseEntity<?> changeUsername(Authentication authentication,
                                            @RequestBody ChangeUsernameRequest body) {
        try {
            UserResponse res = userService.changeUsername(authentication.getName(), body.getNewUsername());
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest body) {
        try { return ResponseEntity.ok(userService.register(body)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMySubject(Authentication authentication) {
        try {
            String username = authentication.getName();
            Subject subject = subjectRepository.findByLinkedUserId(
                    userRepository.findByUsername(username)
                            .orElseThrow(() -> new RuntimeException("User không tồn tại!"))
                            .getId()
            ).orElseThrow(() -> new RuntimeException("Tài khoản chưa liên kết với sinh viên!"));
            return ResponseEntity.ok(subjectService.toDTO(subject));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<PagedResponse<UserResponse>> getAll(
            @RequestParam(defaultValue = "")  String search,
            @RequestParam(defaultValue = "0") int    page,
            @RequestParam(defaultValue = "6") int    size) {
        return ResponseEntity.ok(userService.getAll(search, page, size));
    }

    @PutMapping("/{id}/lock")
    public ResponseEntity<?> lock(@PathVariable Long id) {
        try {
            userService.lockUser(id);
            return ResponseEntity.ok(Map.of("message", "Đã khóa tài khoản!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}/unlock")
    public ResponseEntity<?> unlock(@PathVariable Long id) {
        try {
            userService.unlockUser(id);
            return ResponseEntity.ok(Map.of("message", "Đã mở khóa!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            userService.delete(id);
            return ResponseEntity.ok(Map.of("message", "Xóa thành công!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}

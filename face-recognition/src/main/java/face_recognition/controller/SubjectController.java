package face_recognition.controller;

import face_recognition.dto.request.SubjectRequest;
import face_recognition.dto.response.PagedResponse;
import face_recognition.dto.response.SubjectResponse;
import face_recognition.model.Subject;
import face_recognition.model.User;
import face_recognition.repository.SubjectRepository;
import face_recognition.repository.UserRepository;
import face_recognition.service.SubjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/subjects")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SubjectController {

    private final SubjectService subjectService;
    private final UserRepository userRepository;
    private final SubjectRepository subjectRepository;



    @GetMapping("/me/avatar")
    public ResponseEntity<?> getMyAvatar(Authentication authentication) {
        try {
            String avatarUrl = subjectService.getMyAvatarUrl(authentication.getName());
            return ResponseEntity.ok(Map.of("avatarUrl", avatarUrl));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/avatar")
    public ResponseEntity<?> uploadAvatar(@PathVariable Long id,
                                          @RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(subjectService.uploadAvatar(id, file));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/avatar")
    public ResponseEntity<?> deleteAvatar(@PathVariable Long id) {
        try {
            subjectService.deleteAvatar(id);
            return ResponseEntity.ok(Map.of("message", "Đã xóa ảnh đại diện!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMySubject(Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User không tồn tại!"));
            Subject subject = subjectRepository.findByLinkedUserId(user.getId())
                    .orElseThrow(() -> new RuntimeException("Tài khoản chưa liên kết sinh viên!"));
            return ResponseEntity.ok(subjectService.toDTO(subject));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }


    @GetMapping
    public ResponseEntity<PagedResponse<SubjectResponse>> getAll(
            @RequestParam(defaultValue = "")  String search,
            @RequestParam(required = false)   Long   classId,
            @RequestParam(defaultValue = "0") int    page,
            @RequestParam(defaultValue = "6") int    size,
            Authentication authentication) {
        return ResponseEntity.ok(subjectService.getAll(search, classId, page, size, authentication));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubjectResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(subjectService.getById(id));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody SubjectRequest body, Authentication authentication) {
        try {
            return ResponseEntity.ok(subjectService.create(body, authentication));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody SubjectRequest body, Authentication authentication) {
        try {
            return ResponseEntity.ok(subjectService.update(id, body, authentication));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            subjectService.delete(id);
            return ResponseEntity.ok(Map.of("message", "Xóa thành công!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
package face_recognition.controller;

import face_recognition.service.SubjectLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/subject-link")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SubjectLinkController {

    private final SubjectLinkService subjectLinkService;

    @GetMapping("/unlinked")
    public ResponseEntity<?> getUnlinked(@RequestParam(defaultValue = "") String search) {
        try { return ResponseEntity.ok(subjectLinkService.getUnlinkedSubjects(search)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @PostMapping("/send-code")
    public ResponseEntity<?> sendCode(@RequestBody Map<String, Long> body) {
        try {
            subjectLinkService.sendCode(body.get("subjectId"));
            return ResponseEntity.ok(Map.of("message", "Đã gửi mã xác nhận về email!"));
        } catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @PostMapping("/verify-code")
    public ResponseEntity<?> verifyCode(@RequestBody Map<String, String> body) {
        try {
            Long subjectId = Long.valueOf(body.get("subjectId"));
            String token = subjectLinkService.verifyCode(subjectId, body.get("code"));
            return ResponseEntity.ok(Map.of("token", token));
        } catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @PostMapping("/send-code-email")
    public ResponseEntity<?> sendCodeEmail(@RequestBody Map<String, String> body) {
        try {
            subjectLinkService.sendCodeToEmail(body.get("email"), body.get("fullName"));
            return ResponseEntity.ok(Map.of("message", "Đã gửi mã xác nhận về email!"));
        } catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @PostMapping("/verify-code-email")
    public ResponseEntity<?> verifyCodeEmail(@RequestBody Map<String, String> body) {
        try {
            String token = subjectLinkService.verifyEmailCode(body.get("email"), body.get("code"));
            return ResponseEntity.ok(Map.of("token", token));
        } catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }
}
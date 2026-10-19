package face_recognition.controller;

import face_recognition.dto.response.AttendanceStatsResponse;
import face_recognition.dto.response.PagedResponse;
import face_recognition.dto.request.SessionRequest;
import face_recognition.dto.response.SessionResponse;
import face_recognition.model.User;
import face_recognition.repository.UserRepository;
import face_recognition.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class SessionController {

    private final SessionService sessionService;
    private final UserRepository userRepository;

    @PutMapping("/close-expired")
    public ResponseEntity<?> closeExpired() {
        try {
            int closed = sessionService.closeExpiredSessions();
            return ResponseEntity.ok(Map.of("closed", closed));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/admin/backfill-absent")
    public ResponseEntity<?> backfillAbsent() {
        int processed = sessionService.backfillAbsentForClosedSessions();
        return ResponseEntity.ok(Map.of("processed", processed));
    }

    @GetMapping("/stats/{userId}")
    public ResponseEntity<AttendanceStatsResponse> getAttendanceStats(@PathVariable Long userId) {
        return ResponseEntity.ok(sessionService.getAttendanceStats(userId));
    }

    @GetMapping("/stats/me")
    public ResponseEntity<AttendanceStatsResponse> getMyAttendanceStats(Authentication auth) {
        User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user!"));
        return ResponseEntity.ok(sessionService.getAttendanceStats(user.getId()));
    }

    @GetMapping("/active")
    public ResponseEntity<?> getActive(@RequestParam Long classRoomId) {
        try { return ResponseEntity.ok(sessionService.getActiveSessions(classRoomId)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @GetMapping
    public ResponseEntity<PagedResponse<SessionResponse>> getAll(
            @RequestParam(defaultValue = "")  String search,
            @RequestParam(defaultValue = "")  String status,
            @RequestParam(required = false)   Long   classId,
            @RequestParam(defaultValue = "0") int    page,
            @RequestParam(defaultValue = "8") int    size,
            Authentication authentication) {
        return ResponseEntity.ok(sessionService.getAll(search, status, classId, page, size, authentication));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id, Authentication authentication) {
        try { return ResponseEntity.ok(sessionService.getById(id, authentication)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody SessionRequest body, Authentication auth) {
        try { return ResponseEntity.ok(sessionService.create(body, auth)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody SessionRequest body, Authentication authentication) {
        try { return ResponseEntity.ok(sessionService.update(id, body, authentication)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @PutMapping("/{id}/open")
    public ResponseEntity<?> open(@PathVariable Long id, Authentication authentication) {
        try { return ResponseEntity.ok(sessionService.open(id, authentication)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @PutMapping("/{id}/close")
    public ResponseEntity<?> close(@PathVariable Long id, Authentication authentication) {
        try { return ResponseEntity.ok(sessionService.close(id, authentication)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication authentication) {
        try { sessionService.delete(id, authentication); return ResponseEntity.ok(Map.of("message", "Xóa thành công!")); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }
}
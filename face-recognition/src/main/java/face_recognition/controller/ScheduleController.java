package face_recognition.controller;

import face_recognition.dto.response.PagedResponse;
import face_recognition.dto.request.ScheduleRequest;
import face_recognition.dto.response.ScheduleResponse;
import face_recognition.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class ScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping
    public ResponseEntity<PagedResponse<ScheduleResponse>> getAll(
            @RequestParam(defaultValue = "")  String search,
            @RequestParam(required = false)   Long   classId,
            @RequestParam(defaultValue = "0") int    page,
            @RequestParam(defaultValue = "8") int    size) {
        return ResponseEntity.ok(scheduleService.getAll(search, classId, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        try { return ResponseEntity.ok(scheduleService.getById(id)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody ScheduleRequest body) {
        try { return ResponseEntity.ok(scheduleService.create(body)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody ScheduleRequest body) {
        try { return ResponseEntity.ok(scheduleService.update(id, body)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try { scheduleService.delete(id); return ResponseEntity.ok(Map.of("message", "Đã xóa!")); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @PutMapping("/{id}/toggle")
    public ResponseEntity<?> toggle(@PathVariable Long id) {
        try { return ResponseEntity.ok(scheduleService.toggleActive(id)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestParam(defaultValue = "7") int days) {
        try {
            int count = scheduleService.generateForNextDays(days);
            return ResponseEntity.ok(Map.of(
                    "message", String.format("Đã tạo %d buổi điểm danh!", count),
                    "count", count
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
package face_recognition.controller;


import face_recognition.dto.request.ClassRoomRequest;
import face_recognition.dto.response.ClassRoomResponse;
import face_recognition.dto.response.PagedResponse;
import face_recognition.service.ClassRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;


@RestController
@RequestMapping("/api/classes")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ClassRoomController {

    private final ClassRoomService classRoomService;

    @GetMapping
    public ResponseEntity<PagedResponse<ClassRoomResponse>> getAll(
            @RequestParam(defaultValue = "")  String search,
            @RequestParam(defaultValue = "0") int    page,
            @RequestParam(defaultValue = "6") int    size,
            Authentication authentication) {
        return ResponseEntity.ok(classRoomService.getAll(search, page, size, authentication));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClassRoomResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(classRoomService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> create(@RequestBody ClassRoomRequest body) {
        try {
            return ResponseEntity.ok(classRoomService.create(body));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody ClassRoomRequest body) {
        try {
            return ResponseEntity.ok(classRoomService.update(id, body));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            classRoomService.delete(id);
            return ResponseEntity.ok(Map.of("message", "Xóa thành công!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
package face_recognition.controller;

import face_recognition.dto.RegisterFaceRequest;
import face_recognition.dto.RegisterFaceResponse;
import face_recognition.model.Subject;
import face_recognition.service.FaceRegistrationService;
import face_recognition.service.SubjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/subjects")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FaceRegistrationController {

    private final FaceRegistrationService service;
    private final SubjectService subjectService;

    @PostMapping("/{id}/face/register")
    public RegisterFaceResponse register(@PathVariable Long id,
                                         @RequestBody RegisterFaceRequest req,
                                         Authentication authentication) {
        checkOwnership(id, authentication);
        return service.register(id, req.frames());
    }

    @DeleteMapping("/{id}/face")
    public void reset(@PathVariable Long id, Authentication authentication) {
        checkOwnership(id, authentication);
        service.resetFace(id);
    }

    private void checkOwnership(Long subjectId, Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return;

        Subject me = subjectService.findByUsername(auth.getName());
        if (me == null || !me.getId().equals(subjectId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Bạn không có quyền đăng ký khuôn mặt cho sinh viên này");
        }
    }
}
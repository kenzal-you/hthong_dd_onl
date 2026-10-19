package face_recognition.controller;

import face_recognition.dto.notification.NotificationStudentResponse;
import face_recognition.service.StudentNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class StudentNotificationController {

    private final StudentNotificationService studentNotificationService;
    private final face_recognition.repository.UserRepository userRepository;

    private Long currentUserId(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"))
                .getId();
    }

    @GetMapping
    public Page<NotificationStudentResponse> getMyNotifications(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return studentNotificationService.getMyNotifications(currentUserId(auth), page, size);
    }

    @GetMapping("/unread-count")
    public long unreadCount(Authentication auth) {
        return studentNotificationService.countUnread(currentUserId(auth));
    }

    @PatchMapping("/{id}/read")
    public void markAsRead(@PathVariable Long id, Authentication auth) {
        studentNotificationService.markAsRead(id, currentUserId(auth));
    }

    @PatchMapping("/read-all")
    public void markAllAsRead(Authentication auth) {
        studentNotificationService.markAllAsRead(currentUserId(auth));
    }

    @DeleteMapping("/{id}")
    public void hide(@PathVariable Long id, Authentication auth) {
        studentNotificationService.hideNotification(id, currentUserId(auth));
    }

    @DeleteMapping
    public void hideAll(Authentication auth) {
        studentNotificationService.hideAllNotifications(currentUserId(auth));
    }
}
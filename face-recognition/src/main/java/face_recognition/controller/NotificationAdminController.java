package face_recognition.controller;

import face_recognition.dto.notification.*;
import face_recognition.dto.response.WarnedNotificationSendResponse;
import face_recognition.model.AutoNotificationConfig;
import face_recognition.service.NotificationAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class NotificationAdminController {

    private final NotificationAdminService notificationAdminService;

    @PostMapping("/warned-students/send")
    @PreAuthorize("hasRole('ADMIN')")
    public WarnedNotificationSendResponse sendWarningToWarnedStudents(Authentication authentication) {
        String createdBy = authentication != null ? authentication.getName() : "ADMIN";
        return notificationAdminService.sendWarningToWarnedStudents(createdBy);
    }

    @PatchMapping("/{id}/status")
    public NotificationAdminResponse updateStatus(@PathVariable Long id,
                                                  @RequestBody UpdateNotificationStatusRequest req,
                                                  Authentication authentication) {
        return notificationAdminService.updateStatus(id, req.getIsActive(), authentication);
    }

    @GetMapping
    public Page<NotificationAdminResponse> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "all") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size,
            Authentication authentication) {
        return notificationAdminService.getNotifications(search, status, page, size, authentication);
    }

    @PostMapping
    public NotificationAdminResponse create(@RequestBody CreateNotificationRequest req,
                                            Authentication authentication) {
        String createdBy = authentication != null ? authentication.getName() : "ADMIN";
        return notificationAdminService.createAndSend(req, createdBy, authentication);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, Authentication authentication) {
        notificationAdminService.deleteNotification(id, authentication);
    }

    @GetMapping("/warned-students")
    @PreAuthorize("hasRole('ADMIN')")
    public java.util.List<WarnedStudentResponse> getWarnedStudents() {
        return notificationAdminService.getWarnedStudents();
    }

    @GetMapping("/auto-config")
    @PreAuthorize("hasRole('ADMIN')")
    public AutoNotificationConfig getConfig() {
        return notificationAdminService.getOrCreateConfig();
    }

    @PutMapping("/auto-config")
    @PreAuthorize("hasRole('ADMIN')")
    public AutoNotificationConfig updateConfig(@RequestBody AutoNotificationConfigRequest req) {
        return notificationAdminService.updateConfig(req);
    }
}
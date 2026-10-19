package face_recognition.dto.notification;

import face_recognition.model.NotificationRecipient;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationStudentResponse {
    private Long id;             // id của NotificationRecipient (dùng để mark read / xóa)
    private String title;
    private String message;
    private String type;
    private Boolean isRead;
    private LocalDateTime createdAt;

    public static NotificationStudentResponse from(NotificationRecipient r) {
        var n = r.getNotification();
        return NotificationStudentResponse.builder()
                .id(r.getId())
                .title(n.getTitle())
                .message(n.getContent())
                .type(n.getType().name().toLowerCase())
                .isRead(r.getIsRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
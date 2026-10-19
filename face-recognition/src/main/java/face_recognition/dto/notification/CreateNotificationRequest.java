package face_recognition.dto.notification;

import face_recognition.model.NotificationTarget;
import face_recognition.model.NotificationType;
import lombok.Data;

@Data
public class CreateNotificationRequest {
    private String title;
    private String content;
    private NotificationType type;       // INFO, SUCCESS, WARNING, ERROR
    private NotificationTarget target;    // SINGLE_USER, CLASS, WARNED_STUDENTS, ALL...
    private Long userId;       // dùng khi target = SINGLE_USER
    private Long classRoomId;  // dùng khi target = CLASS
}
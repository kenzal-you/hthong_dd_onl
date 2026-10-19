package face_recognition.dto.notification;

import lombok.Data;

@Data
public class UpdateNotificationStatusRequest {
    private Boolean isActive;
}
package face_recognition.dto.notification;

import lombok.Data;

@Data
public class AutoNotificationConfigRequest {
    private Integer warningThreshold;
    private Integer autoSendThreshold;
    private Boolean enabled;
    private String messageTemplate;
}
package face_recognition.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarnedNotificationSendResponse {
    private int totalStudents;
    private int notificationsSent;
    private int emailsSent;
    private int emailsSkipped;
}
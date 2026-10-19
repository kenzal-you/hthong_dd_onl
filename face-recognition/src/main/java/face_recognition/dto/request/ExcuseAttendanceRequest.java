package face_recognition.dto.request;

import lombok.Data;

@Data
public class ExcuseAttendanceRequest {
    private Long sessionId;
    private Long subjectId;
    private String reason;
}
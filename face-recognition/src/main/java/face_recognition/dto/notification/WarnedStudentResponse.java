package face_recognition.dto.notification;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WarnedStudentResponse {
    private Long userId;
    private Long subjectId;
    private String studentCode;
    private String fullName;
    private String className;
    private long absentCount;
}
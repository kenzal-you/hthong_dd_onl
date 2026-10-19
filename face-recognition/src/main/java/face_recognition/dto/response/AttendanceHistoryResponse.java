package face_recognition.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class AttendanceHistoryResponse {
    private Long          id;
    private Long          sessionId;
    private Long          subjectId;
    private String        sessionName;
    private String        sessionLocation;
    private LocalDateTime sessionStartTime;
    private LocalDateTime sessionEndTime;
    private String        sessionStatus;
    private String        attendanceStatus;
    private LocalDateTime checkedAt;
    private Double        confidence;
    private String        method;
    private String        subjectFullName;
    private String        subjectStudentCode;
    private String        classRoomName;
    private String        note;
}
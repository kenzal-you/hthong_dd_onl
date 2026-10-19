package face_recognition.dto.request;

import lombok.Data;

@Data
public class AttendanceHistoryFilter {
    private Long subjectId;
    private Long classId;
    private Integer month;
    private Integer year;
}
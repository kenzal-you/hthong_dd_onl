package face_recognition.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentAttendanceItem {
    private String subjectFullName;
    private String classRoomName;
    private LocalDateTime checkedAt;
    private String status; // PRESENT / ABSENT / LATE / EXCUSED
}
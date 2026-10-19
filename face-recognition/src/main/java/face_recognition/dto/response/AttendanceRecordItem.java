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
public class AttendanceRecordItem {
    private Long sessionId;
    private String sessionName;
    private LocalDateTime date;
    private String status; // "present" | "absent" (khớp với FE: record.status === 'present')
}
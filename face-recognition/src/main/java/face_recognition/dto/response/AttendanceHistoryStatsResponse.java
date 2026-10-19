package face_recognition.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceHistoryStatsResponse {
    private long total;
    private long present;
    private long absent;
    private long late;
    private long excused;
    private Double avgConfidence;
}
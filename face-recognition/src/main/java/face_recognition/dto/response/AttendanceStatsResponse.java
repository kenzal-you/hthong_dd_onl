package face_recognition.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceStatsResponse {
    private int attendedSessions;   // số buổi đã điểm danh (có mặt)
    private int totalSessions;      // tổng số buổi xét (max 20)
    private double attendanceRate;  // phần trăm, ví dụ 77.78
    private List<AttendanceRecordItem> recentRecords;


}
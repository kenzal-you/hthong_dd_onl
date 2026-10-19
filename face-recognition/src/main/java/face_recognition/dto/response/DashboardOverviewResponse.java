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
public class DashboardOverviewResponse {

    // Số sinh viên đã train khuôn mặt - chưa có API nào trả sẵn nên gộp vào đây
    private long trainedSubjects;

    // Điểm danh 7 ngày gần nhất (Thứ 2 -> CN của tuần hiện tại)
    private List<WeeklyAttendanceItem> weekly;

    // Tỷ lệ điểm danh tổng (toàn bộ dữ liệu, dùng cho donut chart)
    private AttendanceRatioResponse ratio;

    // Các bản ghi điểm danh gần đây nhất (toàn hệ thống)
    private List<RecentAttendanceItem> recent;

    private long linkedAccounts;
}
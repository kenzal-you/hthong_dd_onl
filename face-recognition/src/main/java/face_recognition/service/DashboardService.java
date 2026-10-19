package face_recognition.service;

import face_recognition.dto.response.AttendanceRatioResponse;
import face_recognition.dto.response.DashboardOverviewResponse;
import face_recognition.dto.response.RecentAttendanceItem;
import face_recognition.dto.response.WeeklyAttendanceItem;
import face_recognition.model.AttendanceRecord;
import face_recognition.repository.AttendanceRecordRepository;
import face_recognition.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SubjectRepository subjectRepository;
    private final AttendanceRecordRepository recordRepository;
    private final ClassRoomService classRoomService; // dùng lại resolveTeacherId đã có

    private static final Map<DayOfWeek, String> DAY_LABELS = Map.of(
            DayOfWeek.MONDAY,    "T2",
            DayOfWeek.TUESDAY,   "T3",
            DayOfWeek.WEDNESDAY, "T4",
            DayOfWeek.THURSDAY,  "T5",
            DayOfWeek.FRIDAY,    "T6",
            DayOfWeek.SATURDAY,  "T7",
            DayOfWeek.SUNDAY,    "CN"
    );

    public DashboardOverviewResponse getOverview(Authentication authentication) {
        Long teacherId = classRoomService.resolveTeacherId(authentication); // null nếu là ADMIN

        long trainedSubjects = teacherId != null
                ? subjectRepository.countByFaceRegisteredTrueAndClassRoom_Teacher_Id(teacherId)
                : subjectRepository.countTrained();

        long linkedAccounts = teacherId != null
                ? subjectRepository.countByClassRoom_Teacher_IdAndLinkedUserIsNotNull(teacherId)
                : subjectRepository.countByLinkedUserIsNotNull();

        return DashboardOverviewResponse.builder()
                .trainedSubjects(trainedSubjects)
                .linkedAccounts(linkedAccounts)
                .weekly(buildWeekly(teacherId))
                .ratio(buildRatio(teacherId))
                .recent(buildRecent(teacherId))
                .build();
    }

    // ── Điểm danh 7 ngày của tuần hiện tại (Thứ 2 -> CN) ────────────────────
    private List<WeeklyAttendanceItem> buildWeekly(Long teacherId) {
        LocalDate today     = LocalDate.now();
        LocalDate monday    = today.with(DayOfWeek.MONDAY);
        LocalDateTime start = monday.atStartOfDay();
        LocalDateTime end   = monday.plusDays(7).atStartOfDay();

        List<AttendanceRecord> records = teacherId != null
                ? recordRepository.findBySession_StartTimeBetweenAndSubject_ClassRoom_Teacher_Id(start, end, teacherId)
                : recordRepository.findBySession_StartTimeBetween(start, end);

        List<WeeklyAttendanceItem> result = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate day = monday.plusDays(i);
            long present = 0, absent = 0;

            for (AttendanceRecord r : records) {
                if (r.getSession() == null || r.getSession().getStartTime() == null) continue;
                if (!r.getSession().getStartTime().toLocalDate().equals(day)) continue;

                if (r.getStatus() == AttendanceRecord.AttendanceStatus.PRESENT) present++;
                else if (r.getStatus() == AttendanceRecord.AttendanceStatus.ABSENT) absent++;
            }

            result.add(WeeklyAttendanceItem.builder()
                    .day(DAY_LABELS.get(day.getDayOfWeek()))
                    .date(day)
                    .present(present)
                    .absent(absent)
                    .build());
        }
        return result;
    }

    // ── Tỷ lệ điểm danh (toàn hệ thống với ADMIN, chỉ lớp phụ trách với TEACHER) ──
    private AttendanceRatioResponse buildRatio(Long teacherId) {
        List<AttendanceRecord> records = teacherId != null
                ? recordRepository.findBySubject_ClassRoom_Teacher_Id(teacherId)
                : recordRepository.findAll();

        long present = 0, absent = 0, late = 0, excused = 0;
        for (AttendanceRecord r : records) {
            if (r.getStatus() == null) continue;
            switch (r.getStatus()) {
                case PRESENT -> present++;
                case ABSENT  -> absent++;
                case LATE    -> late++;
                case EXCUSED -> excused++;
            }
        }

        return AttendanceRatioResponse.builder()
                .total(records.size())
                .present(present)
                .absent(absent)
                .late(late)
                .excused(excused)
                .build();
    }

    // ── Các bản ghi điểm danh gần đây nhất ──────────────────────────────────
    private List<RecentAttendanceItem> buildRecent(Long teacherId) {
        List<AttendanceRecord> records = teacherId != null
                ? recordRepository.findBySubject_ClassRoom_Teacher_Id(
                teacherId, PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "checkedAt")))
                : recordRepository
                .findAll(PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "checkedAt")))
                .getContent();

        return records.stream()
                .map(r -> RecentAttendanceItem.builder()
                        .subjectFullName(r.getSubject() != null ? r.getSubject().getFullName() : null)
                        .classRoomName(r.getSubject() != null && r.getSubject().getClassRoom() != null
                                ? r.getSubject().getClassRoom().getName() : null)
                        .checkedAt(r.getCheckedAt())
                        .status(r.getStatus() != null ? r.getStatus().name() : null)
                        .build())
                .collect(Collectors.toList());
    }
}
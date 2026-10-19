package face_recognition.service;

import face_recognition.dto.response.PagedResponse;
import face_recognition.dto.request.ScheduleRequest;
import face_recognition.dto.response.ScheduleResponse;
import face_recognition.model.AttendanceSession;
import face_recognition.model.ClassRoom;
import face_recognition.model.Schedule;
import face_recognition.repository.AttendanceSessionRepository;
import face_recognition.repository.ScheduleRepository;
import face_recognition.specification.ScheduleSpecification;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository          scheduleRepository;
    private final AttendanceSessionRepository sessionRepository;
    private final ClassRoomService            classRoomService;

    private static final Map<DayOfWeek, String> DAY_LABELS = Map.of(
            DayOfWeek.MONDAY,    "Thứ 2",
            DayOfWeek.TUESDAY,   "Thứ 3",
            DayOfWeek.WEDNESDAY, "Thứ 4",
            DayOfWeek.THURSDAY,  "Thứ 5",
            DayOfWeek.FRIDAY,    "Thứ 6",
            DayOfWeek.SATURDAY,  "Thứ 7",
            DayOfWeek.SUNDAY,    "Chủ nhật"
    );

    public PagedResponse<ScheduleResponse> getAll(String search, Long classId, int page, int size) {
        Specification<Schedule> spec = ScheduleSpecification.filter(search, classId);
        PageRequest pageable = PageRequest.of(page, size, Sort.by("dayOfWeek", "startTime"));
        return PagedResponse.of(scheduleRepository.findAll(spec, pageable), this::toDTO);
    }

    public ScheduleResponse getById(Long id) {
        return toDTO(findById(id));
    }

    public ScheduleResponse create(ScheduleRequest request) {
        validate(request);
        Schedule entity = toEntity(request);
        entity.setIsActive(true);
        return toDTO(scheduleRepository.save(entity));
    }

    public ScheduleResponse update(Long id, ScheduleRequest request) {
        validate(request);
        Schedule existing = findById(id);
        updateEntity(existing, request);
        return toDTO(scheduleRepository.save(existing));
    }

    @Transactional
    public void delete(Long id) {
        Schedule schedule = findById(id);
        schedule.setIsDeleted(true);
        scheduleRepository.save(schedule);
    }

    public ScheduleResponse toggleActive(Long id) {
        Schedule schedule = findById(id);
        schedule.setIsActive(!schedule.getIsActive());
        return toDTO(scheduleRepository.save(schedule));
    }

    // ── Tự động sinh Session mỗi ngày lúc 00:01 ─────────────────────────────

    @Scheduled(cron = "0 1 0 * * *")
    public void generateSessionsForToday() {
        LocalDate today    = LocalDate.now();
        DayOfWeek today_dw = today.getDayOfWeek();

        List<Schedule> schedules = scheduleRepository
                .findByIsActiveTrueAndDayOfWeek(today_dw);

        for (Schedule s : schedules) {
            if (s.getValidFrom() != null && today.isBefore(s.getValidFrom())) continue;
            if (s.getValidTo()   != null && today.isAfter(s.getValidTo()))    continue;

            LocalDateTime start = LocalDateTime.of(today, s.getStartTime());
            LocalDateTime end   = s.getEndTime() != null
                    ? LocalDateTime.of(today, s.getEndTime()) : null;

            // Kiểm tra đã có session cho ngày hôm nay chưa
            boolean exists = sessionRepository.findAll().stream().anyMatch(ses ->
                    ses.getClassRoom() != null
                            && s.getClassRoom() != null
                            && ses.getClassRoom().getId().equals(s.getClassRoom().getId())
                            && ses.getStartTime() != null
                            && ses.getStartTime().toLocalDate().equals(today)
                            && ses.getName().equals(buildSessionName(s, today))
            );

            if (!exists) {
                AttendanceSession session = AttendanceSession.builder()
                        .name(buildSessionName(s, today))
                        .location(s.getLocation())
                        .startTime(start)
                        .endTime(end)
                        .classRoom(s.getClassRoom())
                        .status(AttendanceSession.SessionStatus.PENDING)
                        .build();
                sessionRepository.save(session);
                log.info("Auto-generated session: {} for class {}", session.getName(),
                        s.getClassRoom() != null ? s.getClassRoom().getName() : "N/A");
            }
        }
    }

    // ── Thủ công generate cho 1 tuần tới ─────────────────────────────────────

    public int generateForNextDays(int days) {
        int count = 0;
        LocalDate today = LocalDate.now();

        for (int i = 0; i < days; i++) {
            LocalDate date = today.plusDays(i);
            DayOfWeek dw   = date.getDayOfWeek();

            List<Schedule> schedules = scheduleRepository
                    .findByIsActiveTrueAndDayOfWeek(dw);

            for (Schedule s : schedules) {
                if (s.getValidFrom() != null && date.isBefore(s.getValidFrom())) continue;
                if (s.getValidTo()   != null && date.isAfter(s.getValidTo()))    continue;

                String sessionName = buildSessionName(s, date);
                boolean exists = sessionRepository.findAll().stream().anyMatch(ses ->
                        ses.getName().equals(sessionName)
                                && ses.getStartTime() != null
                                && ses.getStartTime().toLocalDate().equals(date)
                );

                if (!exists) {
                    sessionRepository.save(AttendanceSession.builder()
                            .name(sessionName)
                            .location(s.getLocation())
                            .startTime(LocalDateTime.of(date, s.getStartTime()))
                            .endTime(s.getEndTime() != null ? LocalDateTime.of(date, s.getEndTime()) : null)
                            .classRoom(s.getClassRoom())
                            .status(AttendanceSession.SessionStatus.PENDING)
                            .build());
                    count++;
                }
            }
        }
        return count;
    }

    // ── map ──────────────────────────────────────────────────────────────────

    private ScheduleResponse toDTO(Schedule s) {
        return ScheduleResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .classRoomId(s.getClassRoom() != null ? s.getClassRoom().getId() : null)
                .classRoomName(s.getClassRoom() != null ? s.getClassRoom().getName() : null)
                .dayOfWeek(s.getDayOfWeek())
                .dayOfWeekLabel(s.getDayOfWeek() != null ? DAY_LABELS.get(s.getDayOfWeek()) : null)
                .startTime(s.getStartTime())
                .endTime(s.getEndTime())
                .location(s.getLocation())
                .validFrom(s.getValidFrom())
                .validTo(s.getValidTo())
                .isActive(s.getIsActive())
                .build();
    }

    private Schedule toEntity(ScheduleRequest req) {
        Schedule s = new Schedule();
        updateEntity(s, req);
        return s;
    }

    private void updateEntity(Schedule s, ScheduleRequest req) {
        s.setName(req.getName());
        s.setDayOfWeek(req.getDayOfWeek());
        s.setStartTime(req.getStartTime());
        s.setEndTime(req.getEndTime());
        s.setLocation(req.getLocation());
        s.setValidFrom(req.getValidFrom());
        s.setValidTo(req.getValidTo());
        if (req.getIsActive() != null) s.setIsActive(req.getIsActive());
        if (req.getClassRoomId() != null) {
            s.setClassRoom(classRoomService.findById(req.getClassRoomId()));
        }
    }

    public Schedule findById(Long id) {
        return scheduleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lịch id: " + id));
    }

    private void validate(ScheduleRequest r) {
        if (r.getName() == null || r.getName().isBlank())
            throw new RuntimeException("Tên lịch không được để trống!");
        if (r.getDayOfWeek() == null)
            throw new RuntimeException("Ngày trong tuần không được để trống!");
        if (r.getStartTime() == null)
            throw new RuntimeException("Giờ bắt đầu không được để trống!");
        if (r.getValidFrom() == null)
            throw new RuntimeException("Ngày bắt đầu hiệu lực không được để trống!");
        if (r.getValidTo() != null && r.getValidFrom() != null
                && r.getValidTo().isBefore(r.getValidFrom()))
            throw new RuntimeException("Ngày kết thúc phải sau ngày bắt đầu!");
    }

    private String buildSessionName(Schedule s, LocalDate date) {
        return s.getName() + " - " + date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
}
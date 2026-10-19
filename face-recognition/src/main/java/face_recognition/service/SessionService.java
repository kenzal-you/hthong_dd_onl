package face_recognition.service;

import face_recognition.dto.response.AttendanceRecordItem;
import face_recognition.dto.response.AttendanceStatsResponse;
import face_recognition.dto.response.PagedResponse;
import face_recognition.dto.request.SessionRequest;
import face_recognition.dto.response.SessionResponse;
import face_recognition.model.*;
import face_recognition.model.AttendanceSession.SessionStatus;
import face_recognition.repository.AttendanceRecordRepository;
import face_recognition.repository.AttendanceSessionRepository;
import face_recognition.repository.SubjectRepository;
import face_recognition.repository.UserRepository;
import face_recognition.specification.SessionSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceRecordRepository  recordRepository;
    private final ClassRoomService            classRoomService;
    private final UserRepository              userRepository;
    private final SubjectRepository           subjectRepository;
    private static final int MAX_SESSIONS = 20;

    // ── đóng tất cả buổi đang OPEN mà đã quá hạn (endTime < hiện tại) ──────────
    public int closeExpiredSessions() {
        LocalDateTime now = LocalDateTime.now();
        List<AttendanceSession> expired = sessionRepository
                .findByStatusAndEndTimeBefore(SessionStatus.OPEN, now);

        for (AttendanceSession session : expired) {
            session.setStatus(SessionStatus.CLOSED);
            AttendanceSession saved = sessionRepository.save(session);
            markAbsentForUncheckedSubjects(saved);
        }
        return expired.size();
    }

    public int backfillAbsentForClosedSessions() {
        List<AttendanceSession> closed = sessionRepository.findByStatus(SessionStatus.CLOSED);
        closed.forEach(this::markAbsentForUncheckedSubjects);
        return closed.size();
    }

    public AttendanceStatsResponse getAttendanceStats(Long userId) {
        Subject subject = subjectRepository.findByLinkedUser_Id(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ sinh viên gắn với user id: " + userId));

        Long classRoomId = subject.getClassRoom() != null ? subject.getClassRoom().getId() : null;
        if (classRoomId == null) {
            return emptyStats();
        }

        PageRequest pageable = PageRequest.of(0, MAX_SESSIONS, Sort.by(Sort.Direction.DESC, "startTime"));
        List<AttendanceSession> sessions = sessionRepository
                .findByClassRoom_IdAndStatusIn(classRoomId, List.of(SessionStatus.OPEN, SessionStatus.CLOSED), pageable)
                .getContent();

        if (sessions.isEmpty()) {
            return emptyStats();
        }

        List<Long> sessionIds = sessions.stream().map(AttendanceSession::getId).toList();
        List<AttendanceRecord> records = recordRepository.findBySubject_IdAndSession_IdIn(subject.getId(), sessionIds);

        Map<Long, AttendanceRecord.AttendanceStatus> statusMap = records.stream()
                .collect(Collectors.toMap(
                        r -> r.getSession().getId(), AttendanceRecord::getStatus, (a, b) -> b));

        List<AttendanceRecordItem> recentRecords = new ArrayList<>();
        for (AttendanceSession s : sessions) {
            AttendanceRecord.AttendanceStatus status = statusMap.get(s.getId());

            if (status == null) {
                if (s.getStatus() == SessionStatus.CLOSED) {
                    status = AttendanceRecord.AttendanceStatus.ABSENT;
                } else {
                    continue;
                }
            }

            recentRecords.add(AttendanceRecordItem.builder()
                    .sessionId(s.getId())
                    .sessionName(s.getName())
                    .date(s.getStartTime())
                    .status(status.name())
                    .build());
        }

        int totalSessions = recentRecords.size();
        if (totalSessions == 0) {
            return emptyStats();
        }

        long attended = recentRecords.stream()
                .filter(r -> "PRESENT".equals(r.getStatus()) || "LATE".equals(r.getStatus()))
                .count();

        double rate = Math.round((attended * 10000.0) / totalSessions) / 100.0;

        return AttendanceStatsResponse.builder()
                .attendedSessions((int) attended)
                .totalSessions(totalSessions)
                .attendanceRate(rate)
                .recentRecords(recentRecords)
                .build();
    }

    private AttendanceStatsResponse emptyStats() {
        return AttendanceStatsResponse.builder()
                .attendedSessions(0)
                .totalSessions(0)
                .attendanceRate(0)
                .recentRecords(List.of())
                .build();
    }

    public List<SessionResponse> getActiveSessions(Long classRoomId) {
        LocalDateTime now = LocalDateTime.now();
        return sessionRepository.findByStatus(SessionStatus.OPEN).stream()
                .filter(s -> s.getClassRoom() != null && s.getClassRoom().getId().equals(classRoomId))
                .filter(s -> !s.getStartTime().isAfter(now))
                .filter(s -> s.getEndTime() == null || !s.getEndTime().isBefore(now))
                .map(this::toDTO)
                .toList();
    }

    public PagedResponse<SessionResponse> getAll(String search, String status, Long classId, int page, int size,
                                                 Authentication authentication) {
        Long teacherId = classRoomService.resolveTeacherId(authentication);

        // Nếu teacher truyền classId cụ thể, đảm bảo lớp đó thuộc quyền của họ
        if (teacherId != null && classId != null) {
            ClassRoom classRoom = classRoomService.findById(classId);
            boolean owns = classRoom.getTeacher() != null && classRoom.getTeacher().getId().equals(teacherId);
            if (!owns) throw new RuntimeException("Bạn không có quyền xem lớp này!");
        }

        Specification<AttendanceSession> spec = SessionSpecification.filter(search, status, classId, teacherId);
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AttendanceSession> result = sessionRepository.findAll(spec, pageable);
        return PagedResponse.of(result, this::toDTO);
    }

    public SessionResponse getById(Long id, Authentication authentication) {
        AttendanceSession session = findById(id);
        validateTeacherOwnsSession(session, authentication);
        return toDTO(session);
    }

    public List<AttendanceSession> getOpenSessions() {
        return sessionRepository.findByStatus(SessionStatus.OPEN);
    }

    public SessionResponse create(SessionRequest request, Authentication auth) {
        validate(request);

        Long teacherId = classRoomService.resolveTeacherId(auth);
        if (teacherId != null) {
            // Teacher bắt buộc phải gán buổi điểm danh vào đúng lớp mình phụ trách
            if (request.getClassRoomId() == null) {
                throw new RuntimeException("Vui lòng chọn lớp học!");
            }
            ClassRoom classRoom = classRoomService.findById(request.getClassRoomId());
            boolean owns = classRoom.getTeacher() != null && classRoom.getTeacher().getId().equals(teacherId);
            if (!owns) throw new RuntimeException("Bạn không có quyền tạo buổi điểm danh cho lớp này!");
        }

        AttendanceSession entity = toEntity(request);

        if (auth != null) {
            userRepository.findByUsername(auth.getName())
                    .ifPresent(entity::setCreatedBy);
        }

        return toDTO(sessionRepository.save(entity));
    }

    public SessionResponse update(Long id, SessionRequest request, Authentication authentication) {
        validate(request);
        AttendanceSession existing = findById(id);
        if (existing.getStatus() == SessionStatus.CLOSED)
            throw new RuntimeException("Không thể sửa buổi đã đóng!");

        validateTeacherOwnsSession(existing, authentication);

        Long teacherId = classRoomService.resolveTeacherId(authentication);
        if (teacherId != null && request.getClassRoomId() != null) {
            ClassRoom newClassRoom = classRoomService.findById(request.getClassRoomId());
            boolean owns = newClassRoom.getTeacher() != null && newClassRoom.getTeacher().getId().equals(teacherId);
            if (!owns) throw new RuntimeException("Bạn không có quyền chuyển buổi điểm danh sang lớp này!");
        }

        existing.setName(request.getName());
        existing.setLocation(request.getLocation());
        existing.setStartTime(request.getStartTime());
        existing.setEndTime(request.getEndTime());

        if (request.getClassRoomId() != null) {
            ClassRoom classRoom = classRoomService.findById(request.getClassRoomId());
            existing.setClassRoom(classRoom);
        }

        return toDTO(sessionRepository.save(existing));
    }

    public SessionResponse open(Long id, Authentication authentication) {
        AttendanceSession session = findById(id);
        validateTeacherOwnsSession(session, authentication);

        if (session.getStatus() == SessionStatus.OPEN)
            throw new RuntimeException("Buổi điểm danh đã được mở!");
        if (session.getStatus() == SessionStatus.CLOSED)
            throw new RuntimeException("Buổi điểm danh đã đóng, không thể mở lại!");
        session.setStatus(SessionStatus.OPEN);
        return toDTO(sessionRepository.save(session));
    }

    public SessionResponse close(Long id, Authentication authentication) {
        AttendanceSession session = findById(id);
        validateTeacherOwnsSession(session, authentication);

        if (session.getStatus() == SessionStatus.CLOSED)
            throw new RuntimeException("Buổi điểm danh đã đóng!");

        session.setStatus(SessionStatus.CLOSED);
        AttendanceSession saved = sessionRepository.save(session);

        markAbsentForUncheckedSubjects(saved);

        return toDTO(saved);
    }

    // ── tự động gán ABSENT cho sinh viên chưa điểm danh khi đóng buổi ─────────
    private void markAbsentForUncheckedSubjects(AttendanceSession session) {
        if (session.getClassRoom() == null) return;

        List<Subject> classSubjects = subjectRepository.findByClassRoomId(session.getClassRoom().getId());
        if (classSubjects.isEmpty()) return;

        List<Long> alreadyMarked = recordRepository.findSubjectIdsBySessionId(session.getId());

        List<AttendanceRecord> toInsert = classSubjects.stream()
                .filter(subject -> !alreadyMarked.contains(subject.getId()))
                .map(subject -> AttendanceRecord.builder()
                        .session(session)
                        .subject(subject)
                        .status(AttendanceRecord.AttendanceStatus.ABSENT)
                        .method("SYSTEM")
                        .build())
                .toList();

        if (!toInsert.isEmpty()) {
            recordRepository.saveAll(toInsert);
        }
    }

    public void delete(Long id, Authentication authentication) {
        AttendanceSession session = findById(id);
        validateTeacherOwnsSession(session, authentication);

        if (session.getStatus() == SessionStatus.OPEN)
            throw new RuntimeException("Không thể xóa buổi đang mở!");

        Integer totalRecords = recordRepository.countRecordsBySessionId(id);
        if (totalRecords != null && totalRecords > 0)
            throw new RuntimeException("Buổi điểm danh đã có " + totalRecords + " lượt điểm danh, không thể xóa!");

        session.setIsDeleted(true);
        sessionRepository.save(session);
    }

    // Nếu người gọi là TEACHER, buổi điểm danh phải thuộc lớp họ phụ trách
    // (session không gán lớp -> teacher không được thao tác, chỉ ADMIN mới thấy/sửa được các buổi tự do này)
    private void validateTeacherOwnsSession(AttendanceSession session, Authentication authentication) {
        Long teacherId = classRoomService.resolveTeacherId(authentication);
        if (teacherId == null) return; // ADMIN không giới hạn

        boolean owns = session.getClassRoom() != null
                && session.getClassRoom().getTeacher() != null
                && session.getClassRoom().getTeacher().getId().equals(teacherId);
        if (!owns) {
            throw new RuntimeException("Bạn không có quyền thao tác với buổi điểm danh này!");
        }
    }

    // ── map ──────────────────────────────────────────────────────────────────

    private SessionResponse toDTO(AttendanceSession s) {
        Integer total = recordRepository.countRecordsBySessionId(s.getId());
        return SessionResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .location(s.getLocation())
                .startTime(s.getStartTime())
                .endTime(s.getEndTime())
                .status(s.getStatus() != null ? s.getStatus().name() : null)
                .classRoomId(s.getClassRoom() != null ? s.getClassRoom().getId() : null)
                .classRoomName(s.getClassRoom() != null ? s.getClassRoom().getName() : null)
                .createdById(s.getCreatedBy() != null ? s.getCreatedBy().getId() : null)
                .createdByUsername(s.getCreatedBy() != null ? s.getCreatedBy().getUsername() : null)
                .createdAt(s.getCreatedAt())
                .totalRecords(total != null ? total : 0)
                .build();
    }

    private AttendanceSession toEntity(SessionRequest req) {
        AttendanceSession s = new AttendanceSession();
        s.setName(req.getName());
        s.setLocation(req.getLocation());
        s.setStartTime(req.getStartTime());
        s.setEndTime(req.getEndTime());
        if (req.getClassRoomId() != null) {
            s.setClassRoom(classRoomService.findById(req.getClassRoomId()));
        }
        return s;
    }

    public AttendanceSession findById(Long id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy buổi điểm danh id: " + id));
    }

    private void validate(SessionRequest r) {
        if (r.getName() == null || r.getName().isBlank())
            throw new RuntimeException("Tên buổi không được để trống!");
        if (r.getStartTime() == null)
            throw new RuntimeException("Thời gian bắt đầu không được để trống!");
        if (r.getEndTime() != null && r.getEndTime().isBefore(r.getStartTime()))
            throw new RuntimeException("Thời gian kết thúc phải sau thời gian bắt đầu!");
    }
}
package face_recognition.repository;

import face_recognition.model.AttendanceSession;
import face_recognition.model.AttendanceSession.SessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AttendanceSessionRepository extends
        JpaRepository<AttendanceSession, Long>,
        JpaSpecificationExecutor<AttendanceSession> {

    List<AttendanceSession> findByStatus(SessionStatus status);

    @Query("SELECT COUNT(r) FROM AttendanceRecord r WHERE r.session.id = :sessionId")
    Integer countRecordsBySessionId(Long sessionId);

    Page<AttendanceSession> findByClassRoom_Id(Long classRoomId, Pageable pageable);

    Page<AttendanceSession> findByClassRoom_IdAndStatusIn(
            Long classRoomId,
            List<AttendanceSession.SessionStatus> statuses,
            Pageable pageable);

    List<AttendanceSession> findByStatusAndEndTimeBefore(SessionStatus status, LocalDateTime time);


}
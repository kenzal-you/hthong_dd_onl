package face_recognition.repository;

import face_recognition.model.AttendanceRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRecordRepository extends
        JpaRepository<AttendanceRecord, Long>,
        JpaSpecificationExecutor<AttendanceRecord> {

    List<AttendanceRecord> findBySession_StartTimeBetween(LocalDateTime start, LocalDateTime end);

    List<AttendanceRecord> findBySubject_IdAndSession_IdIn(Long subjectId, List<Long> sessionIds);

    List<AttendanceRecord> findBySession_StartTimeBetweenAndSubject_ClassRoom_Teacher_Id(
            LocalDateTime start, LocalDateTime end, Long teacherId);

    List<AttendanceRecord> findBySubject_ClassRoom_Teacher_Id(Long teacherId);

    List<AttendanceRecord> findBySubject_ClassRoom_Teacher_Id(Long teacherId, Pageable pageable);

    @Query("SELECT COUNT(r) FROM AttendanceRecord r WHERE r.session.id = :sessionId")
    Integer countRecordsBySessionId(@Param("sessionId") Long sessionId);
    Optional<AttendanceRecord> findBySessionIdAndSubjectId(Long sessionId, Long subjectId);

    // Số buổi ABSENT của 1 subject (tính từ một thời điểm, ví dụ đầu kỳ)
    @Query("select count(a) from AttendanceRecord a " +
            "where a.subject.id = :subjectId and a.status = 'ABSENT' " +
            "and (:from is null or a.checkedAt >= :from)")
    long countAbsences(@Param("subjectId") Long subjectId,
                       @Param("from") LocalDateTime from);

    // Danh sách subjectId + số buổi nghỉ >= threshold (toàn hệ thống)
    @Query("select a.subject.id, count(a) from AttendanceRecord a " +
            "where a.status = 'ABSENT' and (:from is null or a.checkedAt >= :from) " +
            "group by a.subject.id having count(a) >= :threshold")
    List<Object[]> findSubjectIdsWithAbsencesAtLeast(@Param("threshold") long threshold,
                                                     @Param("from") LocalDateTime from);


    @Query("SELECT r.subject.id FROM AttendanceRecord r WHERE r.session.id = :sessionId")
    List<Long> findSubjectIdsBySessionId(@Param("sessionId") Long sessionId);
}
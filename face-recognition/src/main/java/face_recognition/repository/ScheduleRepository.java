package face_recognition.repository;

import face_recognition.model.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface ScheduleRepository extends
        JpaRepository<Schedule, Long>,
        JpaSpecificationExecutor<Schedule> {

    List<Schedule> findByIsActiveTrueAndDayOfWeek(DayOfWeek dayOfWeek);

    boolean existsByClassRoomIdAndDayOfWeekAndStartTime(
            Long classRoomId, DayOfWeek dayOfWeek, LocalTime startTime);
}
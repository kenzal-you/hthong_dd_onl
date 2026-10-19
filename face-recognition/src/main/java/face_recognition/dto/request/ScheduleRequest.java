package face_recognition.dto.request;

import lombok.Data;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class ScheduleRequest {
    private String    name;
    private Long      classRoomId;
    private DayOfWeek dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private String    location;
    private LocalDate validFrom;
    private LocalDate validTo;
    private Boolean   isActive;
}
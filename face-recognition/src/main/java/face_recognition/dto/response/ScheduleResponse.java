package face_recognition.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
public class ScheduleResponse {
    private Long      id;
    private String    name;
    private Long      classRoomId;
    private String    classRoomName;
    private DayOfWeek dayOfWeek;
    private String    dayOfWeekLabel;
    private LocalTime startTime;
    private LocalTime endTime;
    private String    location;
    private LocalDate validFrom;
    private LocalDate validTo;
    private Boolean   isActive;
}
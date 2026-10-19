package face_recognition.dto.request;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SessionRequest {
    private String        name;
    private String        location;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long          classRoomId;
}
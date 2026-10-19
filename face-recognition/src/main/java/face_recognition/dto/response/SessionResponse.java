package face_recognition.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class SessionResponse {
    private Long          id;
    private String        name;
    private String        location;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String        status;
    private Long          classRoomId;
    private String        classRoomName;
    private Long          createdById;
    private String        createdByUsername;
    private LocalDateTime createdAt;
    private Integer       totalRecords;
}
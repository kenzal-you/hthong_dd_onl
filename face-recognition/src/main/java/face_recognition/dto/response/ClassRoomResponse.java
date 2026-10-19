package face_recognition.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ClassRoomResponse {
    private Long id;
    private String name;
    private String code;
    private String description;
    private Long teacherId;
    private String teacherName;
    private Integer maxStudents;
    private Integer totalSubjects;
    private LocalDateTime createdAt;
}
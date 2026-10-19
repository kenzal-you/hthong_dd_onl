package face_recognition.dto.request;

import lombok.Data;

@Data
public class ClassRoomRequest {
    private String name;
    private String code;
    private String description;
    private Long teacherId;
    private Integer maxStudents;
}
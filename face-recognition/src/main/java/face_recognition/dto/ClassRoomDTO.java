package face_recognition.dto;

import lombok.Data;

@Data
public class ClassRoomDTO {
    private String name;
    private String code;
    private String description;
    private String teacher;
    private Integer maxStudents;
}
package face_recognition.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FaceResultDTO {
    private boolean recognized;
    private String message;
    private Long subjectId;
    private String fullName;
    private String studentCode;
    private String className;
    private Double confidence;
    private Integer faceX;
    private Integer faceY;
    private Integer faceW;
    private Integer faceH;
    private String checkedAt;
    private String sessionName;
    private String dateOfBirth;
    private String phone;
    private String email;
    private String username;
}

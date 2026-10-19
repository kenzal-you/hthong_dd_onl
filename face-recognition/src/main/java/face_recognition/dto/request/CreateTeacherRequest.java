package face_recognition.dto.request;

import lombok.Data;

@Data
public class CreateTeacherRequest {
    private String username;
    private String password;
    private String fullName;
    private String email;
}
package face_recognition.dto.response;

import lombok.Data;

@Data
public class UserProfileResponse {
    private Long id;
    private String username;
    private String fullName;
    private String email;
    private String role;
}
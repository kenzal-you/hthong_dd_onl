package face_recognition.dto.request;

import lombok.Data;

@Data
public class ChangeUsernameRequest {
    private String newUsername;
}
package face_recognition.dto;


import lombok.Data;

@Data
public class FrameDTO {
    private String image;
    private Long sessionId;
    private String username;
}

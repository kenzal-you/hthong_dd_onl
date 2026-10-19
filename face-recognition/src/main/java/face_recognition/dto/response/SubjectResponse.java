
package face_recognition.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class SubjectResponse {
    private Long id;
    private String fullName;
    private String studentCode;
    private LocalDate dateOfBirth;
    private String phone;
    private String email;
    private String address;
    private String notes;
    private Integer faceLabel;
    private Boolean isTrained;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long classRoomId;
    private String classRoomName;
    private String classRoomCode;
    private Long          linkedUserId;
    private String        linkedUsername;
    private String        avatarUrl;
}
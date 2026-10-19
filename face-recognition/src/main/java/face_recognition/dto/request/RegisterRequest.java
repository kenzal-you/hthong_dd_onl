package face_recognition.dto.request;

import lombok.Data;
import java.time.LocalDate;

@Data
public class RegisterRequest {
    // Thông tin User
    private String username;
    private String password;
    private String email;

    // Thông tin Subject (chỉ khi role = USER)
    private String    fullName;
    private String    studentCode;
    private LocalDate dateOfBirth;
    private String    phone;
    private String    address;
    private String    notes;
    private Long      classRoomId;

    private Long linkSubjectId;
    private String linkVerificationToken;

    private String emailVerificationToken;
}

package face_recognition.dto.request;

import lombok.Data;
import java.time.LocalDate;

@Data
public class SubjectRequest {
    private String    fullName;
    private String    studentCode;
    private LocalDate dateOfBirth;
    private String    phone;
    private String    email;
    private String    address;
    private String    notes;
    private Long      classRoomId;
    private Long      linkedUserId;
}
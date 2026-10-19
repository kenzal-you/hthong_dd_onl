package face_recognition.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "session_id")
    private AttendanceSession session;

    @ManyToOne
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @Enumerated(EnumType.STRING)
    private AttendanceStatus status;

    private LocalDateTime checkedAt;

    private Double confidence;

    private String method;

    @Column(length = 500)
    private String note;

    @PrePersist
    public void prePersist() {
        if (status == null) status = AttendanceStatus.PRESENT;
        if (checkedAt == null && (status == AttendanceStatus.PRESENT || status == AttendanceStatus.LATE)) {
            checkedAt = LocalDateTime.now();
        }
    }

    public enum AttendanceStatus {
        PRESENT, ABSENT,LATE, EXCUSED
    }
}
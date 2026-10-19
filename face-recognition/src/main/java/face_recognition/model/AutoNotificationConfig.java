package face_recognition.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "auto_notification_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoNotificationConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "warning_threshold", nullable = false)
    private Integer warningThreshold;

    @Column(name = "auto_send_threshold", nullable = false)
    private Integer autoSendThreshold;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(name = "message_template", columnDefinition = "TEXT", nullable = false)
    private String messageTemplate;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    @PrePersist
    public void touch() {
        this.updatedAt = LocalDateTime.now();
        if (this.enabled == null) this.enabled = true;
        if (this.warningThreshold == null) this.warningThreshold = 3;
        if (this.autoSendThreshold == null) this.autoSendThreshold = 2;
    }
}
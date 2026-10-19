package face_recognition.model;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "system_settings")
public class SystemSetting {
    @Id @GeneratedValue
    private Long id;
    @Column(name = "config_key")
    private String key;           // "confidence_threshold", "min_images"
    private String value;
    private String description;
    private LocalDateTime updatedAt;

    @ManyToOne
    @JoinColumn(name = "updated_by")
    private User updatedBy;
}

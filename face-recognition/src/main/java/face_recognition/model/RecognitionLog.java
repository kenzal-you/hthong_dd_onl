package face_recognition.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "recognition_logs")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class RecognitionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    private Double confidence;

    @Column(name = "recognized_at", updatable = false)
    private LocalDateTime recognizedAt;

    @PrePersist
    public void prePersist() {
        this.recognizedAt = LocalDateTime.now();
    }

    @Column(name = "is_live")
    private Boolean isLive;

    @Column(name = "liveness_method", length = 30)   // "minifasnet" / "svm_lbp" / "laplacian_fallback"
    private String livenessMethod;

    @Column(name = "recognized")
    private Boolean recognized;                       // false = có mặt nhưng không khớp ai

    @Column(name = "latency_ms")
    private Integer latencyMs;



}
package face_recognition.repository;

import face_recognition.model.RecognitionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RecognitionLogRepository extends JpaRepository<RecognitionLog, Long> {
    List<RecognitionLog> findBySubjectIdOrderByRecognizedAtDesc(Long subjectId);
}
package face_recognition.repository;

import face_recognition.model.AutoNotificationConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AutoNotificationConfigRepository extends JpaRepository<AutoNotificationConfig, Long> {
}
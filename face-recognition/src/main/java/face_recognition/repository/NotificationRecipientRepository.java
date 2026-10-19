package face_recognition.repository;

import face_recognition.model.Notification;
import face_recognition.model.NotificationRecipient;
import face_recognition.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, Long> {

    Page<NotificationRecipient> findByUserIdAndIsHiddenFalseAndNotification_IsActiveTrueOrderByNotification_CreatedAtDesc(
            Long userId, Pageable pageable);

    long countByUserIdAndIsHiddenFalseAndIsReadFalseAndNotification_IsActiveTrue(Long userId);

    List<NotificationRecipient> findByUserId(Long userId);

    long countByUserIdAndIsHiddenFalseAndIsReadFalse(Long userId);

    Optional<NotificationRecipient> findByIdAndUserId(Long id, Long userId);

    boolean existsByNotificationAndUser(Notification notification, User user);

    @Modifying
    @Query("update NotificationRecipient nr set nr.isRead = true, nr.readAt = CURRENT_TIMESTAMP " +
            "where nr.user.id = :userId and nr.isRead = false and nr.isHidden = false")
    int markAllAsReadForUser(@Param("userId") Long userId);

    List<NotificationRecipient> findByNotificationId(Long notificationId);
}
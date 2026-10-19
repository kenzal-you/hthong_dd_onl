package face_recognition.service;

import face_recognition.dto.notification.NotificationStudentResponse;
import face_recognition.repository.NotificationRecipientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class StudentNotificationService {

    private final NotificationRecipientRepository recipientRepository;

    public Page<NotificationStudentResponse> getMyNotifications(Long userId, int page, int size) {
        return recipientRepository
                .findByUserIdAndIsHiddenFalseAndNotification_IsActiveTrueOrderByNotification_CreatedAtDesc(
                        userId, PageRequest.of(page, size))
                .map(NotificationStudentResponse::from);
    }

    public long countUnread(Long userId) {
        return recipientRepository.countByUserIdAndIsHiddenFalseAndIsReadFalseAndNotification_IsActiveTrue(userId);
    }



    @Transactional
    public void markAsRead(Long recipientId, Long userId) {
        var recipient = recipientRepository.findByIdAndUserId(recipientId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Thông báo không tồn tại"));
        if (!Boolean.TRUE.equals(recipient.getIsRead())) {
            recipient.setIsRead(true);
            recipient.setReadAt(LocalDateTime.now());
            recipientRepository.save(recipient);
        }
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        recipientRepository.markAllAsReadForUser(userId);
    }

    @Transactional
    public void hideNotification(Long recipientId, Long userId) {
        var recipient = recipientRepository.findByIdAndUserId(recipientId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Thông báo không tồn tại"));
        recipient.setIsHidden(true);
        recipientRepository.save(recipient);
    }

    @Transactional
    public void hideAllNotifications(Long userId) {
        recipientRepository.findByUserIdAndIsHiddenFalseAndNotification_IsActiveTrueOrderByNotification_CreatedAtDesc(
                        userId, PageRequest.of(0, Integer.MAX_VALUE))
                .forEach(r -> r.setIsHidden(true));
    }
}
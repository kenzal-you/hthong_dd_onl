package face_recognition.service;

import face_recognition.client.FaceAiClient;
import face_recognition.dto.AttendanceRecognizeResult;
import face_recognition.dto.ai.AiRecognizeResponse;
import face_recognition.model.AttendanceSession;
import face_recognition.model.Subject;
import face_recognition.repository.AttendanceSessionRepository;
import face_recognition.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AttendanceRecognitionService {

    private final FaceAiClient aiClient;
    private final SubjectRepository subjectRepository;
    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceService attendanceService;
    private final SubjectService subjectService;

    // Luồng chính thức: burst nhiều frame + đủ 2 lớp liveness bên Python. Chỉ ghi điểm danh
    // khi nhận diện được + is_live=true + đúng là chủ tài khoản đang gọi API (kiểm tra lại
    // ở server, không tin client tự so sánh username như React đang làm).
    public AttendanceRecognizeResult attend(Long sessionId, List<String> frames, Double threshold,
                                            Authentication authentication) {

        AttendanceSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return fail(sessionId, null, "Không tìm thấy buổi điểm danh");
        }
        if (session.getEndTime() != null && session.getEndTime().isBefore(LocalDateTime.now())) {
            return fail(sessionId, session.getName(), "Buổi điểm danh đã kết thúc");
        }
        if (session.getStartTime() != null && session.getStartTime().isAfter(LocalDateTime.now())) {
            return fail(sessionId, session.getName(), "Buổi điểm danh chưa bắt đầu");
        }

        AiRecognizeResponse ai = aiClient.recognize(frames, threshold);
        if (ai == null) {
            return fail(sessionId, session.getName(), "Dịch vụ nhận diện không phản hồi");
        }
        if (!ai.recognized()) {
            String reason = !ai.isLive()
                    ? "Không xác thực được đây là người thật (liveness)"
                    : "Không nhận diện được khuôn mặt";
            return fail(sessionId, session.getName(), reason);
        }

        Long subjectId = parseSubjectId(ai.subjectLabel());
        Subject subject = subjectId != null ? subjectRepository.findById(subjectId).orElse(null) : null;
        if (subject == null) {
            return fail(sessionId, session.getName(), "Nhận diện được nhưng không tìm thấy hồ sơ sinh viên");
        }

        Subject me = subjectService.findByUsername(authentication.getName());
        if (me == null || !me.getId().equals(subject.getId())) {
            return fail(sessionId, session.getName(), "Khuôn mặt không khớp với tài khoản đang đăng nhập");
        }

        Double confidencePercent = ai.similarity() != null ? ai.similarity() * 100 : null;
        attendanceService.markPresent(sessionId, subject.getId(), confidencePercent);

        return new AttendanceRecognizeResult(
                true, true, true,
                subject.getLinkedUser() != null ? subject.getLinkedUser().getUsername() : null,
                subject.getFullName(), subject.getStudentCode(),
                confidencePercent, sessionId, session.getName(),
                "Điểm danh thành công"
        );
    }

    private AttendanceRecognizeResult fail(Long sessionId, String sessionName, String message) {
        return new AttendanceRecognizeResult(false, false, false, null, null, null, null,
                sessionId, sessionName, message);
    }

    private Long parseSubjectId(String subjectLabel) {
        try {
            return subjectLabel != null ? Long.parseLong(subjectLabel) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
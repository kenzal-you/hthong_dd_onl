package face_recognition.service;

import face_recognition.client.FaceAiClient;
import face_recognition.dto.RecognizeResult;
import face_recognition.dto.ai.AiRecognizeLiveResponse;
import face_recognition.model.Subject;
import face_recognition.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FaceRecognitionService {

    private final FaceAiClient aiClient;
    private final SubjectRepository subjectRepository;

    // 1 frame đơn, không chạy liveness, không ghi DB — dùng cho preview/tra cứu nhanh.
    // Ai gọi cũng được (ADMIN tra cứu người lạ, sinh viên tự quét thử trước khi điểm danh).
    public RecognizeResult scan(String frame, Double threshold) {
        AiRecognizeLiveResponse ai = aiClient.recognizeLive(frame, threshold);

        if (!ai.faceDetected()) {
            return new RecognizeResult(false, false, null, null, null, null, null,
                    null, null, null, null, null, "Không tìm thấy khuôn mặt");
        }

        Integer x = ai.bbox() != null ? ai.bbox().x() : null;
        Integer y = ai.bbox() != null ? ai.bbox().y() : null;
        Integer w = ai.bbox() != null ? ai.bbox().w() : null;
        Integer h = ai.bbox() != null ? ai.bbox().h() : null;

        if (ai.recognized() == null || !ai.recognized()) {
            return new RecognizeResult(true, false, null, null, null, null, null,
                    ai.similarity(), x, y, w, h, "Không nhận diện được");
        }

        // subjectLabel chính là subjectId dạng String — xem FaceRegistrationService.register()
        // đã gọi aiClient.register(String.valueOf(subjectId), frames)
        Long subjectId = parseSubjectId(ai.subjectLabel());
        Subject subject = subjectId != null ? subjectRepository.findById(subjectId).orElse(null) : null;

        if (subject == null) {
            return new RecognizeResult(true, false, null, null, null, null, null,
                    ai.similarity(), x, y, w, h, "Nhận diện được nhưng không tìm thấy hồ sơ sinh viên");
        }

        return new RecognizeResult(
                true, true, subject.getId(),
                subject.getLinkedUser() != null ? subject.getLinkedUser().getUsername() : null,
                subject.getFullName(), subject.getStudentCode(),
                subject.getClassRoom() != null ? subject.getClassRoom().getName() : null,
                ai.similarity() != null ? ai.similarity() * 100 : null,
                x, y, w, h, "Đã phát hiện khuôn mặt"
        );
    }

    private Long parseSubjectId(String subjectLabel) {
        try {
            return subjectLabel != null ? Long.parseLong(subjectLabel) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
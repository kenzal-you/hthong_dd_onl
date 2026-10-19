package face_recognition.service;

import face_recognition.client.FaceAiClient;
import face_recognition.dto.RegisterFaceResponse;
import face_recognition.dto.ai.AiRegisterResponse;
import face_recognition.model.Subject;
import face_recognition.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FaceRegistrationService {

    private final SubjectRepository subjectRepository;
    private final FaceAiClient aiClient;
    private final SimpMessagingTemplate ws;

    public void resetFace(Long subjectId) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy subject"));

        aiClient.deleteSubject(String.valueOf(subjectId));
        subject.setFaceRegistered(false);
        subjectRepository.save(subject);
    }

    // Cố ý KHÔNG dùng @Transactional: tránh giữ connection DB trong lúc chờ AI xử lý.
    public RegisterFaceResponse register(Long subjectId, List<String> frames) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy subject"));

        AiRegisterResponse ai = aiClient.register(String.valueOf(subjectId), frames);

        if (ai.success()) {
            subject.setFaceRegistered(true);
            subjectRepository.save(subject);
        }

        var result = new RegisterFaceResponse(subjectId, ai.success(), ai.message(),
                ai.imageCount(), ai.livenessDetails(), ai.textureLiveness());

        ws.convertAndSend("/topic/subjects/" + subjectId + "/face-register", result);
        return result;
    }
}
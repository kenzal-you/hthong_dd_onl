package face_recognition.controller;

import face_recognition.dto.RecognizeResult;
import face_recognition.dto.request.WsScanRequest;
import face_recognition.service.FaceRecognitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;



@Controller
@RequiredArgsConstructor
public class FaceWsController {

    private final FaceRecognitionService service;
    private final SimpMessagingTemplate messaging;

    @MessageMapping("/face/scan")
    public void scan(@Payload WsScanRequest req,
                     @Header("simpSessionId") String sessionId) {
        RecognizeResult result;
        try {
            result = service.scan(req.frame(), req.threshold());
        } catch (Exception e) {
            result = new RecognizeResult(false, false, null, null, null, null, null,
                    null, null, null, null, null, "Lỗi: " + e.getMessage());
        }
        SimpMessageHeaderAccessor h = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        h.setSessionId(sessionId);
        h.setLeaveMutable(true);
        messaging.convertAndSendToUser(sessionId, "/queue/face/scan-result",
                result, h.getMessageHeaders());
    }
}
package face_recognition.client;

import face_recognition.dto.ai.AiRecognizeLiveResponse;
import face_recognition.dto.ai.AiRecognizeResponse;
import face_recognition.dto.ai.AiRegisterRequest;
import face_recognition.dto.ai.AiRegisterResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class FaceAiClient {

    private final WebClient pythonWebClient;

    public FaceAiClient(@Qualifier("pythonWebClient") WebClient pythonWebClient) {
        this.pythonWebClient = pythonWebClient;
    }

    public AiRegisterResponse register(String subjectLabel, List<String> frames) {
        try {
            return pythonWebClient.post()
                    .uri("/register")
                    .bodyValue(new AiRegisterRequest(subjectLabel, frames))
                    .retrieve()
                    .bodyToMono(AiRegisterResponse.class)
                    .block(Duration.ofSeconds(30));
        } catch (WebClientResponseException.BadRequest e) {
            // Python trả 400 khi thiếu frame hoặc không thấy mặt
            return new AiRegisterResponse(false,
                    "Không phát hiện được khuôn mặt rõ trong ảnh, vui lòng thử lại",
                    null, null, null);
        } catch (WebClientRequestException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Dịch vụ nhận diện không phản hồi");
        }
    }

    public void deleteSubject(String subjectLabel) {
        try {
            pythonWebClient.delete()
                    .uri("/subject/{label}", subjectLabel)
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(10));
        } catch (WebClientRequestException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Dịch vụ nhận diện không phản hồi");
        }
    }

    // =========================
    // RECOGNIZE LIVE
    // =========================
    public AiRecognizeLiveResponse recognizeLive(
            String frame,
            Double threshold
    ) {
        Map<String, Object> body = new HashMap<>();

        body.put("frame", frame);

        if (threshold != null) {
            body.put("threshold", threshold);
        }

        try {
            return pythonWebClient.post()
                    .uri("/recognize/live")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(AiRecognizeLiveResponse.class)
                    .block(Duration.ofSeconds(30));

        } catch (WebClientRequestException e) {

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Dịch vụ nhận diện không phản hồi"
            );
        }
    }

    // =========================
    // RECOGNIZE MULTIPLE FRAMES
    // =========================
    public AiRecognizeResponse recognize(
            List<String> frames,
            Double threshold
    ) {
        Map<String, Object> body = new HashMap<>();

        body.put("frames", frames);

        if (threshold != null) {
            body.put("threshold", threshold);
        }

        try {
            return pythonWebClient.post()
                    .uri("/recognize")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(AiRecognizeResponse.class)
                    .block(Duration.ofSeconds(30));

        } catch (WebClientRequestException e) {

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Dịch vụ nhận diện không phản hồi"
            );
        }
    }
}
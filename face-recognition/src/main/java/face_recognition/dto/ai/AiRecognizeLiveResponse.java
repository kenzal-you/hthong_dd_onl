package face_recognition.dto.ai;

import java.util.Map;

public record AiRecognizeLiveResponse(
        boolean faceDetected,
        AiBBox bbox,
        Boolean recognized,
        String subjectLabel,
        Double similarity,
        Map<String, Double> latencyMs
) {}
package face_recognition.dto;

import java.util.Map;

public record RegisterFaceResponse(
        Long subjectId,
        boolean success,
        String message,
        Integer imageCount,
        Map<String, Object> livenessDetails,
        Map<String, Object> textureLiveness) {}
package face_recognition.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiRegisterResponse(
        boolean success,
        String message,
        Integer imageCount,
        Map<String, Object> livenessDetails,
        Map<String, Object> textureLiveness) {}
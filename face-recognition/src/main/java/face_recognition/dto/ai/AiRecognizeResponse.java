package face_recognition.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiRecognizeResponse(
        boolean recognized,
        @JsonProperty("isLive") boolean isLive,
        String subjectLabel,
        Double similarity,
        Map<String, Object> livenessDetails,
        Map<String, Object> textureLiveness,
        Map<String, Double> latencyMs
) {}
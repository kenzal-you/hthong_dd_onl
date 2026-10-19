package face_recognition.dto.ai;

import java.util.List;

public record AiRegisterRequest(String subjectLabel, List<String> frames) {}
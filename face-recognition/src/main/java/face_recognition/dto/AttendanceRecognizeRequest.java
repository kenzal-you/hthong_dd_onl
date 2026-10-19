package face_recognition.dto;

import java.util.List;

public record AttendanceRecognizeRequest(Long sessionId, List<String> frames, Double threshold) {}
package face_recognition.dto;

import java.util.List;

public record RegisterFaceRequest(List<String> frames) {}
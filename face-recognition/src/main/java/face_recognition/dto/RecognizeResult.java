package face_recognition.dto;

public record RecognizeResult(
        boolean faceDetected,
        boolean recognized,
        Long subjectId,
        String username,
        String fullName,
        String studentCode,
        String className,
        Double confidence,
        Integer faceX,
        Integer faceY,
        Integer faceW,
        Integer faceH,
        String message
) {}
package face_recognition.dto;

public record AttendanceRecognizeResult(
        boolean recognized,
        boolean isLive,
        boolean attendanceMarked,
        String username,
        String fullName,
        String studentCode,
        Double confidence,
        Long sessionId,
        String sessionName,
        String message
) {}
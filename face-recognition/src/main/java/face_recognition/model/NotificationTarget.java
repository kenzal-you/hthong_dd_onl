package face_recognition.model;

public enum NotificationTarget {
    SINGLE_USER,      // 1 sinh viên cụ thể
    CLASS,            // toàn bộ sinh viên 1 lớp
    WARNED_STUDENTS,  // sinh viên bị cảnh báo (nghỉ > N buổi)
    ALL,              // toàn bộ
    ADMINS,
    TEACHER
}
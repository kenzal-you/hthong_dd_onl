package face_recognition.specification;

import face_recognition.model.AttendanceSession;
import org.springframework.data.jpa.domain.Specification;

public class SessionSpecification {

    public static Specification<AttendanceSession> filter(String search, String status, Long classId, Long teacherId) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();

            if (classId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("classRoom").get("id"), classId));
            }

            if (teacherId != null) {
                predicate = cb.and(predicate,
                        cb.equal(root.get("classRoom").get("teacher").get("id"), teacherId));
            }

            if (status != null && !status.isBlank()) {
                predicate = cb.and(predicate,
                        cb.equal(root.get("status"), AttendanceSession.SessionStatus.valueOf(status)));
            }

            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase() + "%";
                predicate = cb.and(predicate, cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("location"), "")), like)
                ));
            }

            return predicate;
        };
    }
}
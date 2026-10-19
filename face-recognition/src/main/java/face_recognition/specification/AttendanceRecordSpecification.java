package face_recognition.specification;

import face_recognition.model.AttendanceRecord;
import org.springframework.data.jpa.domain.Specification;

public class AttendanceRecordSpecification {

    public static Specification<AttendanceRecord> filter(
            Long subjectId, Long classId, Integer month, Integer year, Long teacherId) {

        return (root, query, cb) -> {
            var predicate = cb.conjunction();

            if (subjectId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("subject").get("id"), subjectId));
            }

            if (classId != null) {
                predicate = cb.and(predicate,
                        cb.equal(root.get("subject").get("classRoom").get("id"), classId));
            }

            if (teacherId != null) {
                predicate = cb.and(predicate,
                        cb.equal(root.get("subject").get("classRoom").get("teacher").get("id"), teacherId));
            }

            if (month != null) {
                predicate = cb.and(predicate,
                        cb.equal(cb.function("MONTH", Integer.class, root.get("session").get("startTime")), month));
            }

            if (year != null) {
                predicate = cb.and(predicate,
                        cb.equal(cb.function("YEAR", Integer.class, root.get("session").get("startTime")), year));
            }

            return predicate;
        };
    }
}
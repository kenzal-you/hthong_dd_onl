package face_recognition.specification;

import face_recognition.model.Subject;
import org.springframework.data.jpa.domain.Specification;

public class SubjectSpecification {

    public static Specification<Subject> filter(String search, Long classId, Long teacherId) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();

            if (classId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("classRoom").get("id"), classId));
            }

            if (teacherId != null) {
                predicate = cb.and(predicate,
                        cb.equal(root.get("classRoom").get("teacher").get("id"), teacherId));
            }

            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase() + "%";
                predicate = cb.and(predicate, cb.or(
                        cb.like(cb.lower(root.get("fullName")), like),
                        cb.like(cb.lower(root.get("studentCode")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("phone"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("email"), "")), like)
                ));
            }

            return predicate;
        };
    }
}
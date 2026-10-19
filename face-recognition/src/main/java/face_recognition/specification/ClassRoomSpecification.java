package face_recognition.specification;

import face_recognition.model.ClassRoom;
import org.springframework.data.jpa.domain.Specification;

public class ClassRoomSpecification {

    public static Specification<ClassRoom> filter(String search, Long teacherId) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();

            if (teacherId != null) {
                predicate = cb.and(predicate,
                        cb.equal(root.get("teacher").get("id"), teacherId));
            }

            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase() + "%";
                predicate = cb.and(predicate, cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("code")), like)
                ));
            }

            return predicate;
        };
    }
}
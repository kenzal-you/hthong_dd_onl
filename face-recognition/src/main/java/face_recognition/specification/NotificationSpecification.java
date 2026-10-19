package face_recognition.specification;

import face_recognition.model.ClassRoom;
import face_recognition.model.Notification;
import face_recognition.model.NotificationTarget;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.experimental.UtilityClass;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class NotificationSpecification {

    public Specification<Notification> withFilters(String search, String status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("content")), like)
                ));
            }

            if (status != null) {
                LocalDateTime now = LocalDateTime.now();
                switch (status) {
                    case "active" -> predicates.add(cb.and(
                            cb.isTrue(root.get("isActive")),
                            cb.or(cb.isNull(root.get("expiredAt")), cb.greaterThan(root.get("expiredAt"), now))
                    ));
                    case "expired" -> predicates.add(cb.or(
                            cb.isFalse(root.get("isActive")),
                            cb.and(cb.isNotNull(root.get("expiredAt")), cb.lessThanOrEqualTo(root.get("expiredAt"), now))
                    ));
                    default -> { /* "all" -> no filter */ }
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Notification> withFiltersForTeacher(
            String search, String status, String teacherUsername, Long teacherId) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));

            if (status != null && !status.equalsIgnoreCase("all")) {
                if (status.equalsIgnoreCase("active")) predicates.add(cb.isTrue(root.get("isActive")));
                else if (status.equalsIgnoreCase("expired")) predicates.add(cb.isFalse(root.get("isActive")));
            }

            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("content")), like)
                ));
            }

            Join<Notification, ClassRoom> cr = root.join("classRoom", JoinType.LEFT);

            Predicate createdByMe = cb.equal(root.get("createdBy"), teacherUsername);
            Predicate sentToMyClass = cb.and(
                    cb.equal(root.get("target"), NotificationTarget.CLASS),
                    cb.equal(cr.join("teacher", JoinType.LEFT).get("id"), teacherId)
            );
            Predicate sentToAll = cb.equal(root.get("target"), NotificationTarget.ALL);

            predicates.add(cb.or(createdByMe, sentToMyClass, sentToAll));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

}
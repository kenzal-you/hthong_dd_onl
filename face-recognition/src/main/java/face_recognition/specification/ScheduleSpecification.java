package face_recognition.specification;

import face_recognition.model.Schedule;
import lombok.experimental.UtilityClass;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

@UtilityClass
public class ScheduleSpecification {

    public Specification<Schedule> filter(String search, Long classId) {
        return (root, query, cb) -> {
            var pred = cb.conjunction();

            if (StringUtils.hasText(search)) {
                String like = "%" + search.toLowerCase() + "%";
                pred = cb.and(pred, cb.or(
                        cb.like(cb.lower(root.get("name")),     like),
                        cb.like(cb.lower(root.get("location")), like)
                ));
            }

            if (classId != null) {
                pred = cb.and(pred,
                        cb.equal(root.get("classRoom").get("id"), classId));
            }

            return pred;
        };
    }
}
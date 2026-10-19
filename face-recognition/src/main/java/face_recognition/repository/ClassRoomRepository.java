package face_recognition.repository;

import face_recognition.model.ClassRoom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ClassRoomRepository extends
        JpaRepository<ClassRoom, Long>,
        JpaSpecificationExecutor<ClassRoom> {

    boolean existsByCode(String code);
}
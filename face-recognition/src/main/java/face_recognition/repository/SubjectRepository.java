package face_recognition.repository;

import face_recognition.model.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface SubjectRepository extends
        JpaRepository<Subject, Long>,
        JpaSpecificationExecutor<Subject> {


    long countByFaceRegisteredTrueAndClassRoom_Teacher_Id(Long teacherId);
    long countByClassRoom_Teacher_IdAndLinkedUserIsNotNull(Long teacherId);
    long countByLinkedUserIsNotNull();


    List<Subject> findByLinkedUserIsNullAndIsDeletedFalse();

    @Query("SELECT COUNT(s) FROM Subject s WHERE s.faceRegistered = true")
    long countTrained();

    Optional<Subject> findByLinkedUser_Id(Long userId);

    Optional<Subject> findByLinkedUser_Username(String username);


    long countByClassRoomId(Long classRoomId);


    Optional<Subject> findByLinkedUserId(Long userId);
    List<Subject> findByClassRoomId(Long classRoomId);
}
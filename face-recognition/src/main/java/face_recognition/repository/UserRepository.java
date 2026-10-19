package face_recognition.repository;

import face_recognition.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends
        JpaRepository<User, Long>,
        JpaSpecificationExecutor<User> {

    boolean existsByUsername (String username);
    List<User> findByRole(User.Role role);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);
}
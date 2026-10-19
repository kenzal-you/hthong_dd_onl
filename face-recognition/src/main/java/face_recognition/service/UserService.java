package face_recognition.service;

import face_recognition.client.FaceAiClient;
import face_recognition.dto.request.ChangePasswordRequest;
import face_recognition.dto.request.CreateTeacherRequest;
import face_recognition.dto.request.RegisterRequest;
import face_recognition.dto.response.PagedResponse;
import face_recognition.dto.response.UserResponse;
import face_recognition.model.ClassRoom;
import face_recognition.model.Subject;
import face_recognition.model.User;
import face_recognition.repository.SubjectRepository;
import face_recognition.repository.UserRepository;
import face_recognition.specification.UserSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SubjectRepository subjectRepository;
    private final ClassRoomService classRoomService;
    private final SubjectLinkService subjectLinkService;
    private final SubjectService subjectService;
    private final FaceAiClient faceAiClient;

    public java.util.List<UserResponse> getAllTeachers() {
        return userRepository.findByRole(User.Role.TEACHER)
                .stream().map(this::toDTO).toList();
    }

    @Transactional
    public UserResponse createTeacher(CreateTeacherRequest request) {
        if (request.getUsername() == null || request.getUsername().isBlank())
            throw new RuntimeException("Username không được để trống!");
        if (request.getPassword() == null || request.getPassword().length() < 6)
            throw new RuntimeException("Mật khẩu phải ít nhất 6 ký tự!");
        if (request.getFullName() == null || request.getFullName().isBlank())
            throw new RuntimeException("Họ tên không được để trống!");

        if (userRepository.existsByUsername(request.getUsername()))
            throw new RuntimeException("Username đã tồn tại!");

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            if (!request.getEmail().matches("^[A-Za-z0-9+_.-]+@(.+)$"))
                throw new RuntimeException("Email không hợp lệ!");
            if (userRepository.existsByEmail(request.getEmail()))
                throw new RuntimeException("Email này đã được sử dụng bởi tài khoản khác!");
        }

        User user = new User();
        user.setUsername(request.getUsername().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName().trim());
        user.setEmail(request.getEmail() != null ? request.getEmail().trim() : null);
        user.setRole(User.Role.TEACHER);
        user.setIsLocked(false);

        User saved = userRepository.save(user);
        return toDTO(saved);
    }


    @Transactional
    public UserResponse changeEmail(String currentUsername, String newEmail) {
        if (newEmail == null || newEmail.isBlank())
            throw new RuntimeException("Email không được để trống!");

        newEmail = newEmail.trim();

        if (!newEmail.matches("^[A-Za-z0-9+_.-]+@(.+)$"))
            throw new RuntimeException("Email không hợp lệ!");

        User user = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new RuntimeException("User không tồn tại!"));

        if (newEmail.equals(user.getEmail())) {
            return toDTO(user);
        }

        if (userRepository.existsByEmail(newEmail))
            throw new RuntimeException("Email này đã được sử dụng bởi tài khoản khác!");

        user.setEmail(newEmail);
        User saved = userRepository.save(user);
        return toDTO(saved);
    }

    @Transactional
    public UserResponse changeUsername(String currentUsername, String newUsername) {
        if (newUsername == null || newUsername.isBlank())
            throw new RuntimeException("Tên đăng nhập không được để trống!");

        newUsername = newUsername.trim();

        if (newUsername.equals(currentUsername)) {
            User user = userRepository.findByUsername(currentUsername)
                    .orElseThrow(() -> new RuntimeException("User không tồn tại!"));
            return toDTO(user);
        }

        if (userRepository.existsByUsername(newUsername))
            throw new RuntimeException("Tên đăng nhập đã tồn tại!");

        User user = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new RuntimeException("User không tồn tại!"));
        user.setUsername(newUsername);
        User saved = userRepository.save(user);

        return toDTO(saved);
    }

    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User không tồn tại!"));

        if (request.getOldPassword() == null || request.getOldPassword().isBlank())
            throw new RuntimeException("Vui lòng nhập mật khẩu hiện tại!");

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword()))
            throw new RuntimeException("Mật khẩu hiện tại không đúng!");

        if (!request.getNewPassword().equals(request.getConfirmPassword()))
            throw new RuntimeException("Xác nhận mật khẩu không khớp!");

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername()))
            throw new RuntimeException("Username đã tồn tại!");
        if (request.getPassword() == null || request.getPassword().length() < 6)
            throw new RuntimeException("Mật khẩu phải ít nhất 6 ký tự!");

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(User.Role.USER);
        user.setIsLocked(false);

        Subject subject;

        if (request.getLinkSubjectId() != null) {
            Long verifiedId = subjectLinkService.consumeToken(
                    request.getLinkVerificationToken(), request.getLinkSubjectId());

            subject = subjectRepository.findById(verifiedId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy sinh viên!"));

            if (subject.getLinkedUser() != null)
                throw new RuntimeException("Sinh viên này đã được liên kết với tài khoản khác!");

            user.setEmail(subject.getEmail());
            user.setFullName(subject.getFullName());

        } else {
            if (request.getFullName() == null || request.getFullName().isBlank())
                throw new RuntimeException("Họ tên sinh viên không được để trống!");
            if (request.getStudentCode() == null || request.getStudentCode().isBlank())
                throw new RuntimeException("Mã sinh viên không được để trống!");
            if (request.getClassRoomId() == null)
                throw new RuntimeException("Phải chọn lớp!");

            if (request.getPhone() != null && !request.getPhone().isEmpty()
                    && !request.getPhone().matches("^[0-9]+$")) {
                throw new RuntimeException("Số điện thoại chỉ được chứa chữ số!");
            }
            if (request.getEmail() != null && !request.getEmail().isEmpty()
                    && !request.getEmail().matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
                throw new RuntimeException("Email không hợp lệ!");
            }
            if (request.getEmail() != null && !request.getEmail().isBlank()) {
                subjectLinkService.consumeEmailToken(request.getEmailVerificationToken(), request.getEmail());
            }

            user.setEmail(request.getEmail());
            user.setFullName(request.getFullName());


            subject = new Subject();
            subject.setFullName(request.getFullName());
            subject.setStudentCode(request.getStudentCode());
            subject.setDateOfBirth(request.getDateOfBirth());
            subject.setPhone(request.getPhone());
            subject.setEmail(request.getEmail());
            subject.setAddress(request.getAddress());
            subject.setNotes(request.getNotes());


            ClassRoom classRoom = classRoomService.findById(request.getClassRoomId());
            subject.setClassRoom(classRoom);
        }

        User savedUser = userRepository.save(user);
        subject.setLinkedUser(savedUser);
        subjectRepository.save(subject);

        return toDTO(savedUser);
    }

    public face_recognition.dto.response.PagedResponse<UserResponse> getAll(String search, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("id").ascending());
        Specification<User> spec = UserSpecification.filter(search);
        Page<User> result = userRepository.findAll(spec, pageable);
        return PagedResponse.of(result, this::toDTO);
    }

    public void lockUser(Long id) {
        User user = findById(id);
        if (isAdmin(user)) throw new RuntimeException("Không thể khóa tài khoản ADMIN!");
        user.setIsLocked(true);
        userRepository.save(user);
    }

    public void unlockUser(Long id) {
        User user = findById(id);
        user.setIsLocked(false);
        userRepository.save(user);
    }

    @Transactional
    public void delete(Long id) {
        User user = findById(id);
        if (isAdmin(user)) throw new RuntimeException("Không thể xóa tài khoản ADMIN!");

        user.setIsDeleted(true);
        user.setIsLocked(true);
        userRepository.save(user);

        Subject subject = user.getSubject();
        if (subject != null) {
            subject.setIsDeleted(true);
            subjectRepository.save(subject);
            try {
                faceAiClient.deleteSubject(String.valueOf(subject.getId()));
            } catch (Exception ignored) {}
        }
    }

    // ── map ──────────────────────────────────────────────

    public UserResponse toDTO(User entity) {
        return UserResponse.builder()
                .id(entity.getId())
                .username(entity.getUsername())
                .fullName(entity.getFullName())
                .email(entity.getEmail())
                .role(entity.getRole() != null ? entity.getRole().name() : null)
                .isLocked(entity.getIsLocked())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    // ── internal ─────────────────────────────────────────

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user id: " + id));
    }

    private boolean isAdmin(User user) {
        return user.getRole() != null && user.getRole().name().equals("ADMIN");
    }
}
package face_recognition.service;

import face_recognition.client.FaceAiClient;
import face_recognition.dto.request.SubjectRequest;
import face_recognition.dto.response.PagedResponse;
import face_recognition.dto.response.SubjectResponse;

import face_recognition.model.ClassRoom;
import face_recognition.model.RecognitionLog;
import face_recognition.model.Subject;
import face_recognition.model.User;
import face_recognition.repository.RecognitionLogRepository;
import face_recognition.repository.SubjectRepository;
import face_recognition.repository.UserRepository;
import face_recognition.specification.SubjectSpecification;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubjectService {

    private final SubjectRepository       subjectRepository;
    private final UserRepository userRepository;
    private final RecognitionLogRepository logRepository;
    private final ClassRoomService        classRoomService;
    private final FaceAiClient faceAiClient;
    private static final String AVATAR_DIR = "./avatars_db/";
    private static final Set<String> ALLOWED_AVATAR_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");

    public String getMyAvatarUrl(String username) {
        return subjectRepository.findByLinkedUser_Username(username)
                .map(Subject::getAvatarPath)
                .map(path -> "/uploads/avatars/" + path)
                .orElse(null);
    }

    public SubjectResponse uploadAvatar(Long id, MultipartFile file) throws IOException {
        Subject subject = findById(id);

        if (file == null || file.isEmpty())
            throw new RuntimeException("Vui lòng chọn ảnh!");
        if (file.getContentType() == null || !ALLOWED_AVATAR_TYPES.contains(file.getContentType()))
            throw new RuntimeException("Chỉ chấp nhận ảnh JPG, PNG hoặc WEBP!");
        if (file.getSize() > 5 * 1024 * 1024)
            throw new RuntimeException("Ảnh không được vượt quá 5MB!");

        Path dir = Paths.get(AVATAR_DIR);
        Files.createDirectories(dir);

        deletePhysicalAvatar(subject.getAvatarPath());

        String ext = extractExtension(file.getOriginalFilename());
        String filename = "subject_" + id + "_" + UUID.randomUUID() + ext;
        Path target = dir.resolve(filename);
        file.transferTo(target);

        subject.setAvatarPath(filename);
        subjectRepository.save(subject);

        return toDTO(subject);
    }

    public void deleteAvatar(Long id) {
        Subject subject = findById(id);
        deletePhysicalAvatar(subject.getAvatarPath());
        subject.setAvatarPath(null);
        subjectRepository.save(subject);
    }

    private void deletePhysicalAvatar(String avatarFilename) {
        if (avatarFilename == null || avatarFilename.isBlank()) return;
        try {
            Files.deleteIfExists(Paths.get(AVATAR_DIR).resolve(avatarFilename));
        } catch (IOException ignored) {
        }
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains("."))
            return "";
        return originalFilename.substring(originalFilename.lastIndexOf('.'));
    }


    public Subject findByUsername(String username) {
        return subjectRepository.findByLinkedUser_Username(username)
                .orElse(null);
    }


    public List<Subject> findAll(){
        return subjectRepository.findAll();
    }

    public PagedResponse<SubjectResponse> getAll(String search, Long classId, int page, int size,
                                                 Authentication authentication) {

        Long teacherId = classRoomService.resolveTeacherId(authentication);

        if (teacherId != null && classId != null) {
            ClassRoom classRoom = classRoomService.findById(classId);
            boolean owns = classRoom.getTeacher() != null
                    && classRoom.getTeacher().getId().equals(teacherId);
            if (!owns) {
                throw new RuntimeException("Bạn không có quyền xem lớp này!");
            }
        }

        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Specification<Subject> spec = SubjectSpecification.filter(search, classId, teacherId);
        Page<Subject> result = subjectRepository.findAll(spec, pageable);
        return PagedResponse.of(result, this::toDTO);
    }

    public SubjectResponse getById(Long id) {
        return toDTO(findById(id));
    }

    public Subject findById(Long id) {
        return subjectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy subject id: " + id));
    }


    public SubjectResponse create(SubjectRequest request, Authentication authentication) {
        validate(request);

        ClassRoom classRoom = classRoomService.findById(request.getClassRoomId());
        validateClassCapacity(classRoom, null);
        validateTeacherOwnsClass(classRoom, authentication);

        Subject entity = toEntity(request);
        return toDTO(subjectRepository.save(entity));
    }

    private void validateClassCapacity(ClassRoom classRoom, Long excludeSubjectId) {
        if (classRoom == null || classRoom.getMaxStudents() == null) return;

        long currentCount = subjectRepository.countByClassRoomId(classRoom.getId());

        if (excludeSubjectId != null) {
            Subject existing = subjectRepository.findById(excludeSubjectId).orElse(null);
            if (existing != null && existing.getClassRoom() != null
                    && existing.getClassRoom().getId().equals(classRoom.getId())) {
                currentCount -= 1;
            }
        }

        if (currentCount >= classRoom.getMaxStudents()) {
            throw new RuntimeException(
                    "Lớp \"" + classRoom.getName() + "\" đã đủ sĩ số (" + classRoom.getMaxStudents() + " sinh viên)!");
        }
    }

    public SubjectResponse update(Long id, SubjectRequest request, Authentication authentication) {
        validate(request);

        ClassRoom classRoom = classRoomService.findById(request.getClassRoomId());
        validateClassCapacity(classRoom, id);
        validateTeacherOwnsClass(classRoom, authentication);

        Subject existing = findById(id);

        // Nếu sinh viên đang thuộc 1 lớp khác, teacher cũng phải đang phụ trách lớp cũ đó
        // (chặn trường hợp teacher A sửa thông tin sinh viên đang thuộc lớp của teacher B)
        if (existing.getClassRoom() != null) {
            validateTeacherOwnsClass(existing.getClassRoom(), authentication);
        }

        updateEntity(existing, request);
        return toDTO(subjectRepository.save(existing));
    }

    // Nếu người gọi là TEACHER, lớp được chọn/đang có phải do chính họ phụ trách
    private void validateTeacherOwnsClass(ClassRoom classRoom, Authentication authentication) {
        Long teacherId = classRoomService.resolveTeacherId(authentication);
        if (teacherId == null) return; // ADMIN thì không giới hạn

        if (classRoom.getTeacher() == null || !classRoom.getTeacher().getId().equals(teacherId)) {
            throw new RuntimeException("Bạn không có quyền thêm/sửa sinh viên ở lớp này!");
        }
    }

    @Transactional
    public void delete(Long id) {
        Subject subject = findById(id);
        subject.setIsDeleted(true);
        subjectRepository.save(subject);

        try {
            faceAiClient.deleteSubject(String.valueOf(id));
        } catch (Exception e) {
            // Python tắt thì vẫn xoá được bên Java; embedding cũ sẽ bị dọn sau
            System.err.println("Không xoá được embedding của subject " + id + ": " + e.getMessage());
        }

        User linkedUser = subject.getLinkedUser();
        if (linkedUser != null) {
            linkedUser.setIsDeleted(true);
            linkedUser.setIsLocked(true);
            userRepository.save(linkedUser);
        }
    }

    public void markAllTrained() {
        List<Subject> all = subjectRepository.findAll();
        subjectRepository.saveAll(all);
    }

    public void logRecognition(Long subjectId, Double confidence) {
        Subject subject = findById(subjectId);
        RecognitionLog log = RecognitionLog.builder()
                .subject(subject)
                .confidence(confidence)
                .build();
        logRepository.save(log);
    }

    // ── map ──────────────────────────────────────────────

    public SubjectResponse toDTO(Subject entity) {
        return SubjectResponse.builder()
                .id(entity.getId())
                .fullName(entity.getFullName())
                .studentCode(entity.getStudentCode())
                .dateOfBirth(entity.getDateOfBirth())
                .phone(entity.getPhone())
                .email(entity.getEmail())
                .address(entity.getAddress())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .classRoomId(entity.getClassRoom() != null ? entity.getClassRoom().getId() : null)
                .classRoomName(entity.getClassRoom() != null ? entity.getClassRoom().getName() : null)
                .classRoomCode(entity.getClassRoom() != null ? entity.getClassRoom().getCode() : null)
                .linkedUserId(entity.getLinkedUser() != null ? entity.getLinkedUser().getId() : null)
                .linkedUsername(entity.getLinkedUser() != null ? entity.getLinkedUser().getUsername() : null)
                .avatarUrl(entity.getAvatarPath() != null
                        ? "/uploads/avatars/" + entity.getAvatarPath()
                        : null)
                .build();
    }

    private Subject toEntity(SubjectRequest request) {
        Subject subject = new Subject();
        subject.setFullName(request.getFullName());
        subject.setStudentCode(request.getStudentCode());
        subject.setDateOfBirth(request.getDateOfBirth());
        subject.setPhone(request.getPhone());
        subject.setEmail(request.getEmail());
        subject.setAddress(request.getAddress());
        subject.setNotes(request.getNotes());

        if (request.getClassRoomId() != null) {
            subject.setClassRoom(classRoomService.findById(request.getClassRoomId()));
        }

        if (request.getLinkedUserId() != null) {
            userRepository.findById(request.getLinkedUserId())
                    .ifPresent(subject::setLinkedUser);
        }
        return subject;
    }

    private void updateEntity(Subject entity, SubjectRequest request) {
        entity.setFullName(request.getFullName());
        entity.setStudentCode(request.getStudentCode());
        entity.setDateOfBirth(request.getDateOfBirth());
        entity.setPhone(request.getPhone());
        entity.setEmail(request.getEmail());
        entity.setAddress(request.getAddress());
        entity.setNotes(request.getNotes());

        if (request.getClassRoomId() != null) {
            entity.setClassRoom(classRoomService.findById(request.getClassRoomId()));
        } else {
            entity.setClassRoom(null);
        }
    }

    private void validate(SubjectRequest r) {
        if (r.getFullName() == null || r.getFullName().isBlank())
            throw new RuntimeException("Họ tên không được để trống!");
        if (r.getStudentCode() == null || r.getStudentCode().isBlank())
            throw new RuntimeException("Mã sinh viên không được để trống!");
        if (r.getClassRoomId() == null)
            throw new RuntimeException("Phải chọn lớp!");
        if (r.getPhone() != null && !r.getPhone().isBlank()
                && !r.getPhone().matches("^0[0-9]{9}$"))
            throw new RuntimeException("SĐT không hợp lệ!");
        if (r.getEmail() != null && !r.getEmail().isBlank()
                && !r.getEmail().matches("^[\\w.]+@[\\w.]+\\.[a-z]{2,}$"))
            throw new RuntimeException("Email không hợp lệ!");
    }
}
package face_recognition.service;

import face_recognition.dto.request.ClassRoomRequest;
import face_recognition.dto.response.ClassRoomResponse;
import face_recognition.dto.response.PagedResponse;
import face_recognition.model.ClassRoom;
import face_recognition.model.User;
import face_recognition.repository.ClassRoomRepository;
import face_recognition.repository.UserRepository;
import face_recognition.specification.ClassRoomSpecification;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClassRoomService {

    private final ClassRoomRepository classRoomRepository;
    private final UserRepository userRepository;

    public PagedResponse<ClassRoomResponse> getAll(String search, int page, int size, Authentication authentication) {
        Long teacherId = resolveTeacherId(authentication);
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Specification<ClassRoom> spec = ClassRoomSpecification.filter(search, teacherId);
        Page<ClassRoom> result = classRoomRepository.findAll(spec, pageable);
        return PagedResponse.of(result, this::toDTO);
    }

    public Long resolveTeacherId(Authentication authentication) {
        if (authentication == null) return null;

        boolean isTeacher = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_TEACHER"));
        if (!isTeacher) return null;

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User không tồn tại!"));
        return user.getId();
    }


    public ClassRoomResponse getById(Long id) {
        return toDTO(findById(id));
    }

    public ClassRoomResponse create(ClassRoomRequest request) {
        validate(request, null);
        ClassRoom entity = toEntity(request);
        return toDTO(classRoomRepository.save(entity));
    }

    public ClassRoomResponse update(Long id, ClassRoomRequest request) {
        validate(request, id);
        ClassRoom existing = findById(id);
        updateEntity(existing, request);
        return toDTO(classRoomRepository.save(existing));
    }

    @Transactional
    public void delete(Long id) {
        ClassRoom classRoom = findById(id);
        if (classRoom.getSubjects() != null && !classRoom.getSubjects().isEmpty())
            throw new RuntimeException("Lớp đang có " + classRoom.getSubjects().size() + " sinh viên, không thể xóa!");

        classRoom.setIsDeleted(true);
        classRoomRepository.save(classRoom);
    }

    // ── map ──────────────────────────────────────────────

    public ClassRoomResponse toDTO(ClassRoom entity) {
        return ClassRoomResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .code(entity.getCode())
                .description(entity.getDescription())
                .teacherId(entity.getTeacher() != null ? entity.getTeacher().getId() : null)
                .teacherName(entity.getTeacher() != null ? entity.getTeacher().getFullName() : null)
                .maxStudents(entity.getMaxStudents())
                .totalSubjects(entity.getSubjects() != null ? entity.getSubjects().size() : 0)
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private ClassRoom toEntity(ClassRoomRequest request) {
        return ClassRoom.builder()
                .name(request.getName())
                .code(request.getCode())
                .description(request.getDescription())
                .teacher(resolveTeacher(request.getTeacherId()))
                .maxStudents(request.getMaxStudents())
                .build();
    }

    private void updateEntity(ClassRoom entity, ClassRoomRequest request) {
        entity.setName(request.getName());
        entity.setCode(request.getCode());
        entity.setDescription(request.getDescription());
        entity.setTeacher(resolveTeacher(request.getTeacherId()));
        entity.setMaxStudents(request.getMaxStudents());
    }

    // ── internal ─────────────────────────────────────────

    public ClassRoom findById(Long id) {
        return classRoomRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lớp id: " + id));
    }

    private User resolveTeacher(Long teacherId) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy giáo viên id: " + teacherId));
        if (teacher.getRole() != User.Role.TEACHER)
            throw new RuntimeException("Tài khoản được chọn không phải giáo viên!");
        return teacher;
    }

    private void validate(ClassRoomRequest r, Long excludeId) {
        if (r.getName() == null || r.getName().isBlank())
            throw new RuntimeException("Tên lớp không được để trống!");
        if (r.getCode() == null || r.getCode().isBlank())
            throw new RuntimeException("Mã lớp không được để trống!");
        if (r.getTeacherId() == null)
            throw new RuntimeException("Vui lòng chọn giáo viên phụ trách!");
        if (r.getMaxStudents() == null || r.getMaxStudents() < 1)
            throw new RuntimeException("Sĩ số tối đa phải lớn hơn 0!");
        boolean exists = classRoomRepository.existsByCode(r.getCode());
        if (exists && excludeId == null)
            throw new RuntimeException("Mã lớp đã tồn tại!");
    }
}
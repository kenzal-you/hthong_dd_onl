package face_recognition.service;

import face_recognition.dto.response.AttendanceHistoryStatsResponse;
import face_recognition.dto.response.PagedResponse;
import face_recognition.dto.request.AttendanceHistoryFilter;
import face_recognition.dto.response.AttendanceHistoryResponse;
import face_recognition.model.AttendanceRecord;
import face_recognition.model.AttendanceSession;
import face_recognition.model.Subject;
import face_recognition.repository.AttendanceRecordRepository;
import face_recognition.repository.AttendanceSessionRepository;
import face_recognition.repository.SubjectRepository;
import face_recognition.specification.AttendanceRecordSpecification;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRecordRepository  recordRepository;
    private final AttendanceSessionRepository sessionRepository;
    private final SubjectRepository           subjectRepository;
    private final ClassRoomService            classRoomService; // dùng lại resolveTeacherId đã có

    public AttendanceHistoryStatsResponse getHistoryStats(AttendanceHistoryFilter filter, Authentication authentication) {
        validateFilter(filter);
        Long teacherId = classRoomService.resolveTeacherId(authentication);
        validateTeacherScope(filter, teacherId);

        Specification<AttendanceRecord> spec = AttendanceRecordSpecification.filter(
                filter.getSubjectId(), filter.getClassId(), filter.getMonth(), filter.getYear(), teacherId
        );

        List<AttendanceRecord> records = recordRepository.findAll(spec);

        long present = 0, absent = 0, late = 0, excused = 0;
        double confSum = 0;
        int confCount = 0;

        for (AttendanceRecord r : records) {
            if (r.getStatus() != null) {
                switch (r.getStatus()) {
                    case PRESENT -> present++;
                    case ABSENT  -> absent++;
                    case LATE    -> late++;
                    case EXCUSED -> excused++;
                }
            }
            if (r.getConfidence() != null) {
                confSum += r.getConfidence();
                confCount++;
            }
        }

        return AttendanceHistoryStatsResponse.builder()
                .total(records.size())
                .present(present)
                .absent(absent)
                .late(late)
                .excused(excused)
                .avgConfidence(confCount > 0 ? confSum / confCount : null)
                .build();
    }

    public void cancelExcused(Long sessionId, Long subjectId, Authentication authentication) {
        AttendanceRecord record = recordRepository.findBySessionIdAndSubjectId(sessionId, subjectId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bản ghi điểm danh!"));

        validateTeacherOwnsSubject(record.getSubject(), authentication);

        if (record.getStatus() != AttendanceRecord.AttendanceStatus.EXCUSED) {
            throw new RuntimeException("Bản ghi này không ở trạng thái có phép!");
        }

        record.setStatus(AttendanceRecord.AttendanceStatus.ABSENT);
        record.setNote(null);
        record.setMethod("SYSTEM");

        recordRepository.save(record);
    }

    public void markExcused(Long sessionId, Long subjectId, String reason, Authentication authentication) {
        AttendanceSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy buổi điểm danh!"));
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy subject!"));

        validateTeacherOwnsSubject(subject, authentication);

        AttendanceRecord record = recordRepository.findBySessionIdAndSubjectId(sessionId, subjectId)
                .orElseGet(() -> AttendanceRecord.builder()
                        .session(session)
                        .subject(subject)
                        .checkedAt(LocalDateTime.now())
                        .method("MANUAL")
                        .build());

        if (record.getId() == null) {
            record.setMethod("MANUAL");
        }
        record.setStatus(AttendanceRecord.AttendanceStatus.EXCUSED);
        record.setNote(reason);

        recordRepository.save(record);
    }

    public void markPresent(Long sessionId, Long subjectId, Double confidence) {
        AttendanceSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy buổi điểm danh!"));
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy subject!"));

        Optional<AttendanceRecord> existing =
                recordRepository.findBySessionIdAndSubjectId(sessionId, subjectId);
        if (existing.isPresent()) return;

        recordRepository.save(AttendanceRecord.builder()
                .session(session)
                .subject(subject)
                .status(AttendanceRecord.AttendanceStatus.PRESENT)
                .checkedAt(LocalDateTime.now())
                .confidence(confidence)
                .method("FACE")
                .build());
    }

    public PagedResponse<AttendanceHistoryResponse> getHistory(
            AttendanceHistoryFilter filter, int page, int size, Authentication authentication) {

        validateFilter(filter);
        Long teacherId = classRoomService.resolveTeacherId(authentication);
        validateTeacherScope(filter, teacherId);

        Specification<AttendanceRecord> spec = AttendanceRecordSpecification.filter(
                filter.getSubjectId(), filter.getClassId(), filter.getMonth(), filter.getYear(), teacherId
        );

        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "session.startTime"));

        Page<AttendanceRecord> result = recordRepository.findAll(spec, pageable);
        return PagedResponse.of(result, this::toDTO);
    }

    public byte[] exportExcel(AttendanceHistoryFilter filter, Authentication authentication) throws IOException {
        validateFilter(filter);
        Long teacherId = classRoomService.resolveTeacherId(authentication);
        validateTeacherScope(filter, teacherId);

        Specification<AttendanceRecord> spec = AttendanceRecordSpecification.filter(
                filter.getSubjectId(), filter.getClassId(), filter.getMonth(), filter.getYear(), teacherId
        );

        List<AttendanceRecord> records = recordRepository.findAll(spec,
                Sort.by(Sort.Direction.DESC, "session.startTime"));

        boolean byClassMode = filter.getSubjectId() == null;

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Lịch sử điểm danh");

            CellStyle headerStyle = buildHeaderStyle(wb);
            CellStyle altStyle    = buildAltStyle(wb);

            List<String>  cols   = new ArrayList<>(List.of("STT"));
            List<Integer> widths = new ArrayList<>(List.of(2000));
            if (byClassMode) {
                cols.add("Sinh viên");
                widths.add(5500);
            }
            cols.addAll(List.of(
                    "Buổi điểm danh", "Địa điểm",
                    "Thời gian bắt đầu", "Thời gian điểm danh",
                    "Trạng thái", "Độ chính xác", "Phương thức"
            ));
            widths.addAll(List.of(6000, 4000, 5000, 5000, 4000, 4000, 3500));

            Row header = sheet.createRow(0);
            for (int i = 0; i < cols.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols.get(i));
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, widths.get(i));
            }

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

            for (int i = 0; i < records.size(); i++) {
                AttendanceRecord r   = records.get(i);
                Row              row = sheet.createRow(i + 1);
                CellStyle        bg  = (i % 2 == 1) ? altStyle : null;

                int col = 0;
                setCell(row, col++, String.valueOf(i + 1), bg);
                if (byClassMode) {
                    String label = r.getSubject() != null
                            ? r.getSubject().getFullName()
                            + (r.getSubject().getStudentCode() != null ? " (" + r.getSubject().getStudentCode() + ")" : "")
                            : "—";
                    setCell(row, col++, label, bg);
                }
                setCell(row, col++, r.getSession() != null ? r.getSession().getName()     : "—", bg);
                setCell(row, col++, r.getSession() != null ? r.getSession().getLocation() : "—", bg);
                setCell(row, col++, r.getSession() != null && r.getSession().getStartTime() != null ? r.getSession().getStartTime().format(fmt) : "—", bg);
                setCell(row, col++, r.getCheckedAt() != null ? r.getCheckedAt().format(fmt) : "—", bg);
                setCell(row, col++, statusLabel(r.getStatus()), bg);
                setCell(row, col++, r.getConfidence() != null ? String.format("%.1f%%", r.getConfidence()) : "—", bg);
                setCell(row, col,   r.getMethod() != null ? r.getMethod() : "—", bg);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ── map ──────────────────────────────────────────────────────────────────

    private AttendanceHistoryResponse toDTO(AttendanceRecord r) {
        return AttendanceHistoryResponse.builder()
                .id(r.getId())
                .sessionId(r.getSession() != null ? r.getSession().getId() : null)
                .subjectId(r.getSubject() != null ? r.getSubject().getId() : null)
                .sessionName(     r.getSession() != null ? r.getSession().getName()     : null)
                .sessionLocation( r.getSession() != null ? r.getSession().getLocation() : null)
                .sessionStartTime(r.getSession() != null ? r.getSession().getStartTime(): null)
                .sessionEndTime(  r.getSession() != null ? r.getSession().getEndTime()  : null)
                .sessionStatus(   r.getSession() != null && r.getSession().getStatus() != null
                        ? r.getSession().getStatus().name() : null)
                .attendanceStatus(r.getStatus() != null ? r.getStatus().name() : null)
                .checkedAt(r.getCheckedAt())
                .confidence(r.getConfidence())
                .method(r.getMethod())
                .note(r.getNote())
                .subjectFullName(   r.getSubject() != null ? r.getSubject().getFullName()    : null)
                .subjectStudentCode(r.getSubject() != null ? r.getSubject().getStudentCode() : null)
                .classRoomName(     r.getSubject() != null && r.getSubject().getClassRoom() != null
                        ? r.getSubject().getClassRoom().getName() : null)
                .build();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void validateFilter(AttendanceHistoryFilter filter) {
        if (filter.getSubjectId() == null && filter.getClassId() == null) {
            throw new RuntimeException("Vui lòng chọn sinh viên hoặc chọn lớp học!");
        }
    }

    // Nếu người gọi là TEACHER, subjectId/classId trong filter phải thuộc phạm vi họ quản lý
    // (chặn trường hợp gõ tay subjectId/classId của lớp khác qua Postman)
    private void validateTeacherScope(AttendanceHistoryFilter filter, Long teacherId) {
        if (teacherId == null) return; // ADMIN không giới hạn

        if (filter.getSubjectId() != null) {
            Subject subject = subjectRepository.findById(filter.getSubjectId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy subject!"));
            boolean owns = subject.getClassRoom() != null
                    && subject.getClassRoom().getTeacher() != null
                    && subject.getClassRoom().getTeacher().getId().equals(teacherId);
            if (!owns) throw new RuntimeException("Bạn không có quyền xem sinh viên này!");
        }

        if (filter.getClassId() != null) {
            // Cách nhẹ nhất: kiểm tra qua 1 subject bất kỳ thuộc lớp đó có teacher đúng không.
            // Nếu ClassRoomRepository đã có sẵn cách check nhanh hơn (vd findById rồi so teacher),
            // có thể thay đoạn dưới bằng cách gọi thẳng classRoomService.findById(filter.getClassId())
            // và so classRoom.getTeacher().getId().equals(teacherId).
        }
    }

    // Dùng cho excuse/cancel-excuse: subject cụ thể phải thuộc lớp của teacher đang gọi
    private void validateTeacherOwnsSubject(Subject subject, Authentication authentication) {
        Long teacherId = classRoomService.resolveTeacherId(authentication);
        if (teacherId == null) return; // ADMIN không giới hạn

        boolean owns = subject.getClassRoom() != null
                && subject.getClassRoom().getTeacher() != null
                && subject.getClassRoom().getTeacher().getId().equals(teacherId);
        if (!owns) {
            throw new RuntimeException("Bạn không có quyền thao tác với sinh viên này!");
        }
    }

    private String statusLabel(AttendanceRecord.AttendanceStatus status) {
        if (status == null) return "—";
        return switch (status) {
            case PRESENT -> "Có mặt";
            case ABSENT  -> "Vắng mặt";
            case LATE    -> "Đi muộn";
            case EXCUSED -> "Có phép";
        };
    }

    private void setCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        if (style != null) cell.setCellStyle(style);
    }

    private CellStyle buildHeaderStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        return s;
    }

    private CellStyle buildAltStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }
}
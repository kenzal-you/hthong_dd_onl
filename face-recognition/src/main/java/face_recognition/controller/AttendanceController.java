package face_recognition.controller;

import face_recognition.dto.AttendanceRecognizeRequest;
import face_recognition.dto.request.ExcuseAttendanceRequest;
import face_recognition.dto.response.PagedResponse;
import face_recognition.dto.request.AttendanceHistoryFilter;
import face_recognition.dto.response.AttendanceHistoryResponse;
import face_recognition.service.AttendanceRecognitionService;
import face_recognition.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final AttendanceRecognitionService recognitionService; // thêm vào field list đầu class

    @PostMapping("/recognize")
    public ResponseEntity<?> recognizeForAttendance(@RequestBody AttendanceRecognizeRequest req,
                                                    Authentication authentication) {
        return ResponseEntity.ok(recognitionService.attend(
                req.sessionId(), req.frames(), req.threshold(), authentication));
    }
    @PostMapping("/cancel-excuse")
    public ResponseEntity<?> cancelExcused(@RequestBody ExcuseAttendanceRequest request, Authentication authentication) {
        try {
            attendanceService.cancelExcused(request.getSessionId(), request.getSubjectId(), authentication);
            return ResponseEntity.ok(Map.of("message", "Đã bỏ gán có phép, chuyển về vắng mặt!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/excuse")
    public ResponseEntity<?> markExcused(@RequestBody ExcuseAttendanceRequest request, Authentication authentication) {
        try {
            attendanceService.markExcused(
                    request.getSessionId(), request.getSubjectId(), request.getReason(), authentication);
            return ResponseEntity.ok(Map.of("message", "Đã gán có phép thành công!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/history/stats")
    public ResponseEntity<?> getHistoryStats(
            @RequestParam(required = false) Long    subjectId,
            @RequestParam(required = false) Long    classId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            Authentication authentication) {
        try {
            AttendanceHistoryFilter filter = new AttendanceHistoryFilter();
            filter.setSubjectId(subjectId);
            filter.setClassId(classId);
            filter.setMonth(month);
            filter.setYear(year);

            return ResponseEntity.ok(attendanceService.getHistoryStats(filter, authentication));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<?> getHistory(
            @RequestParam(required = false) Long    subjectId,
            @RequestParam(required = false) Long    classId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        try {
            AttendanceHistoryFilter filter = new AttendanceHistoryFilter();
            filter.setSubjectId(subjectId);
            filter.setClassId(classId);
            filter.setMonth(month);
            filter.setYear(year);

            return ResponseEntity.ok(attendanceService.getHistory(filter, page, size, authentication));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/history/export")
    public ResponseEntity<?> exportExcel(
            @RequestParam(required = false) Long    subjectId,
            @RequestParam(required = false) Long    classId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            Authentication authentication) {
        try {
            AttendanceHistoryFilter filter = new AttendanceHistoryFilter();
            filter.setSubjectId(subjectId);
            filter.setClassId(classId);
            filter.setMonth(month);
            filter.setYear(year);

            byte[] data     = attendanceService.exportExcel(filter, authentication);
            String filename = "lichsu_diemdanh_"
                    + (subjectId != null ? subjectId : ("lop" + classId))
                    + (month != null ? "_T" + month : "")
                    + (year  != null ? "_" + year   : "") + ".xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(data);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
package face_recognition.controller;

import face_recognition.dto.RecognizeRequest;
import face_recognition.dto.RecognizeResult;
import face_recognition.service.FaceRecognitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recognition")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RecognitionController {

    private final FaceRecognitionService service;

    @PostMapping("/scan")
    public RecognizeResult scan(@RequestBody RecognizeRequest req) {
        return service.scan(req.frame(), req.threshold());
    }
}
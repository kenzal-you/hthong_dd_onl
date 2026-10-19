"""
================================================================================
VAI TRÒ FILE NÀY:
Module chống giả mạo (Liveness Detection / Anti-spoofing) — chạy quanh bước
nhận diện để chặn ảnh tĩnh/ảnh in/ảnh chụp màn hình, tránh ghi nhận điểm danh
sai hoặc lãng phí tài nguyên chạy ArcFace cho ảnh giả.

THIẾT KẾ: HYBRID LIVENESS (2 lớp phòng thủ độc lập, ĐÂY LÀ ĐIỂM NGHIÊN CỨU
CHÍNH của module này — kết hợp 1 kỹ thuật hình học + 1 kỹ thuật machine
learning để bù trừ điểm yếu cho nhau):

  Lớp 1 — ACTIVE LIVENESS (check_blink_sequence, không cần training):
    Eye Aspect Ratio (EAR) phát hiện hành vi CHỚP MẮT qua chuỗi ảnh liên tiếp.
    Tham khảo: Soukupová & Čech (2016), "Real-Time Eye Blink Detection using
    Facial Landmarks", 21st CVWW. Landmark lấy từ MediaPipe Face Mesh (pretrained,
    không train lại — công thức EAR là công thức hình học thuần túy).
    Điểm yếu: có thể bị vượt qua bằng video replay đã có sẵn cảnh chớp mắt.

  Lớp 2 — PASSIVE LIVENESS (texture_ml_check, CÓ TRAINING — xem
    research/train_liveness_svm.py): SVM huấn luyện trên đặc trưng LBP
    (Local Binary Pattern) để phân biệt texture da thật vs. ảnh in/chụp lại
    màn hình trên 1 khung hình đơn lẻ, không phụ thuộc chuyển động.
    Tham khảo: Määttä, Hadid & Pietikäinen (2011), "Face Spoofing Detection
    From Single Images Using Micro-Texture Analysis", IJCB.
    Điểm yếu: phụ thuộc chất lượng camera/ánh sáng, cần dữ liệu train đa dạng.

  -> Kết hợp 2 lớp: kẻ tấn công muốn vượt qua cần vừa giả được chuyển động
  chớp mắt (video replay) VỪA giả được texture da thật (in ảnh chất lượng cao)
  cùng lúc — khó hơn đáng kể so với chỉ dùng 1 lớp đơn lẻ. Đây là nguyên lý
  "defense in depth" phổ biến trong thiết kế hệ thống an toàn, áp dụng cho
  bài toán anti-spoofing.

NẾU CHƯA TRAIN SVM (chưa chạy research/train_liveness_svm.py): hệ thống tự
động fallback về Laplacian variance (đo độ nét bề mặt) làm tín hiệu passive
liveness tạm thời — vẫn chạy được, chỉ là độ tin cậy thấp hơn SVM đã train.

Giới hạn cần ghi rõ trong báo cáo (Chương 5 - Hạn chế & hướng phát triển):
- Chưa chống được tấn công 3D mask hoặc video độ nét rất cao có chớp mắt thật.
- Hướng phát triển: depth camera, hoặc CNN passive-liveness huấn luyện trên
  tập chuẩn lớn hơn (CelebA-Spoof) khi có đủ tài nguyên.
================================================================================
"""

import os

import cv2
import mediapipe as mp
import numpy as np

from app.config import settings
from app.core.minifas_engine import MiniFASNetEngine
from app.core.texture_features import extract_lbp_histogram

mp_face_mesh = mp.solutions.face_mesh

# Chỉ số landmark mắt trái/phải trong 468 điểm của MediaPipe Face Mesh
LEFT_EYE = [362, 385, 387, 263, 373, 380]
RIGHT_EYE = [33, 160, 158, 133, 153, 144]

LIVENESS_SVM_PATH = os.path.join("models", "liveness_svm.pkl")


def _eye_aspect_ratio(landmarks, eye_idx, w, h) -> float:
    pts = [(landmarks[i].x * w, landmarks[i].y * h) for i in eye_idx]
    p1, p2, p3, p4, p5, p6 = pts

    def dist(a, b):
        return float(np.linalg.norm(np.array(a) - np.array(b)))

    vertical = dist(p2, p6) + dist(p3, p5)
    horizontal = dist(p1, p4)
    if horizontal == 0:
        return 0.0
    return vertical / (2.0 * horizontal)


def _laplacian_variance(img_bgr: np.ndarray, bbox) -> float:
    x1, y1, x2, y2 = [int(v) for v in bbox]
    x1, y1 = max(0, x1), max(0, y1)
    crop = img_bgr[y1:y2, x1:x2]
    if crop.size == 0:
        return 0.0
    gray = cv2.cvtColor(crop, cv2.COLOR_BGR2GRAY)
    return float(cv2.Laplacian(gray, cv2.CV_64F).var())


class LivenessChecker:
    def __init__(self) -> None:
        self.mesh = mp_face_mesh.FaceMesh(
            static_image_mode=True,
            max_num_faces=1,
            refine_landmarks=True,
            min_detection_confidence=0.5,
        )

        # Lớp 2 (passive liveness) — ƯU TIÊN 1: MiniFASNet pretrained (CNN, mạnh
        # nhất, không bị domain gap/overfitting như SVM tự train). ƯU TIÊN 2 (dự
        # phòng nếu thiếu model MiniFASNet): SVM tự train. ƯU TIÊN 3 (dự phòng
        # cuối): Laplacian variance.
        self.minifas_engine = None
        try:
            self.minifas_engine = MiniFASNetEngine()
            print("[Liveness] Da load MiniFASNet (pretrained CNN anti-spoofing)")
        except Exception as e:
            print(f"[Liveness] Khong load duoc MiniFASNet ({e}) - fallback sang SVM/Laplacian")

        self.svm_model = None
        if os.path.exists(LIVENESS_SVM_PATH):
            import joblib  # import trễ để service vẫn chạy được nếu thiếu joblib khi chưa cần

            self.svm_model = joblib.load(LIVENESS_SVM_PATH)
            print(f"[Liveness] Da load SVM anti-spoofing tu {LIVENESS_SVM_PATH}")
        else:
            print("[Liveness] Chua co model SVM (models/liveness_svm.pkl) - dung fallback Laplacian variance.")

    # ---------- Lớp 1: Active liveness (blink detection) ----------
    def check_blink_sequence(self, frames_bgr: list[np.ndarray]):
        """
        frames_bgr: danh sách ảnh (BGR) chụp liên tiếp trong ~2-3 giây (burst).
        Trả về (is_live: bool, details: dict) để log/debug và đưa vào response.
        """
        ear_seq = []
        for frame in frames_bgr:
            h, w = frame.shape[:2]
            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            result = self.mesh.process(rgb)
            if not result.multi_face_landmarks:
                ear_seq.append(None)
                continue
            landmarks = result.multi_face_landmarks[0].landmark
            left = _eye_aspect_ratio(landmarks, LEFT_EYE, w, h)
            right = _eye_aspect_ratio(landmarks, RIGHT_EYE, w, h)
            ear_seq.append(round((left + right) / 2.0, 4))

        valid = [e for e in ear_seq if e is not None]
        if len(valid) < max(3, len(frames_bgr) // 2):
            return False, {
                "reason": "Không đủ frame phát hiện được mắt/khuôn mặt",
                "ear_sequence": ear_seq,
            }

        min_ear = min(valid)
        blinked = min_ear < settings.LIVENESS_EAR_THRESHOLD
        # Yêu cầu mắt phải MỞ LẠI sau khi chớp (tránh trường hợp ảnh tĩnh mắt nhắm)
        recovered = valid[-1] > settings.LIVENESS_EAR_THRESHOLD

        is_live = blinked and recovered
        return is_live, {
            "ear_sequence": ear_seq,
            "min_ear": min_ear,
            "blinked": blinked,
            "recovered": recovered,
        }

    # ---------- Lớp 2: Passive liveness (MiniFASNet ưu tiên, SVM/Laplacian dự phòng) ----------
    def texture_ml_check(self, frame_bgr: np.ndarray, bbox):
        """Trả về (is_live: bool, score: float, method: str).

        Thứ tự ưu tiên: MiniFASNet (pretrained CNN) -> SVM (LBP, tự train) ->
        Laplacian variance (fallback cuối cùng nếu chưa có model nào)."""
        if self.minifas_engine is not None:
            result = self.minifas_engine.predict(frame_bgr, bbox)
            return result["is_real"], result["scores"]["real"], "minifasnet"

        x1, y1, x2, y2 = [int(v) for v in bbox]
        x1, y1 = max(0, x1), max(0, y1)
        crop = frame_bgr[y1:y2, x1:x2]
        if crop.size == 0:
            return True, 0.0, "no_crop"  # không đủ dữ liệu để đánh giá -> không chặn oan

        if self.svm_model is not None:
            feat = extract_lbp_histogram(crop).reshape(1, -1)
            proba_real = float(self.svm_model.predict_proba(feat)[0][1])
            return proba_real >= 0.5, proba_real, "svm_lbp"

        score = _laplacian_variance(frame_bgr, bbox)
        return score >= settings.LIVENESS_TEXTURE_MIN_VAR, score, "laplacian_fallback"

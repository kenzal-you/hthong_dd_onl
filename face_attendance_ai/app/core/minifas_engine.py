"""
================================================================================
VAI TRÒ FILE NÀY:
Wrapper cho model MiniFASNet (Silent-Face-Anti-Spoofing, Minivision AI) —
model CNN pretrained chuyên dụng cho anti-spoofing, huấn luyện trên CelebA-Spoof
(70,000+ ảnh). Đây là LỚP 2 (passive liveness) CHÍNH THỨC đang dùng trong hệ
thống, thay cho SVM tự train (vẫn giữ code SVM làm tài liệu nghiên cứu/fallback
dự phòng — xem app/core/liveness.py).

HỌC THUẬT:
Kiến trúc MiniFASNet — Minivision AI, đạt giải Chalearn Face Anti-spoofing
Attack Detection Challenge (CVPR 2020 Workshop). Output 3 lớp: Paper Photo /
Real Face / Screen Photo. Kết hợp 2 model (MiniFASNetV2 + V1SE, mỗi model với
1 tỉ lệ crop mở rộng khác nhau quanh bbox khuôn mặt: 2.7x và 4.0x) rồi trung
bình xác suất softmax — kỹ thuật "model ensemble/fusion" giúp ổn định hơn so
với dùng 1 model đơn lẻ.

VÌ SAO DÙNG PRETRAINED THAY VÌ TỰ TRAIN (giống lý do đã dùng với ArcFace):
Dữ liệu tự thu thập (vài trăm ảnh, 1-2 người, 1 phòng) không thể sánh được về
độ đa dạng với CelebA-Spoof (hàng chục nghìn ảnh, nhiều người, nhiều thiết bị,
nhiều điều kiện ánh sáng) — dùng pretrained tránh được vấn đề overfitting và
domain gap mà nhóm đã phát hiện qua thực nghiệm SVM (xem research/).

Nguồn: https://github.com/QingHeYang/Silent-Face-Anti-Spoofing-onnx
Dựa trên: https://github.com/minivision-ai/Silent-Face-Anti-Spoofing
================================================================================
"""

from pathlib import Path
from typing import Dict, Tuple

import cv2
import numpy as np
import onnxruntime as ort


class MiniFASNetEngine:
    def __init__(self, model_dir: str = "models/minifas") -> None:
        self.model_dir = Path(model_dir)
        self.input_size = (80, 80)
        self.sessions = {}
        self._load_models()

    def _load_models(self) -> None:
        if not self.model_dir.exists():
            raise FileNotFoundError(f"Không tìm thấy thư mục model: {self.model_dir}")

        onnx_files = list(self.model_dir.glob("*.onnx"))
        if not onnx_files:
            raise FileNotFoundError(f"Không có file .onnx nào trong {self.model_dir}")

        for onnx_file in onnx_files:
            session = ort.InferenceSession(str(onnx_file), providers=["CPUExecutionProvider"])
            self.sessions[onnx_file.name] = session

        if not self.sessions:
            raise RuntimeError("Không load được model MiniFASNet nào")

    @staticmethod
    def _get_new_box(src_w: int, src_h: int, bbox: list, scale: float) -> Tuple[int, int, int, int]:
        """Mở rộng bbox theo hệ số scale, giữ tâm, giới hạn trong biên ảnh.
        Logic giữ nguyên y hệt bản gốc Silent-Face-Anti-Spoofing để đảm bảo
        tiền xử lý khớp chính xác với lúc model được train."""
        x, y, box_w, box_h = bbox
        scale = min((src_h - 1) / box_h, min((src_w - 1) / box_w, scale))

        new_width = box_w * scale
        new_height = box_h * scale
        center_x = box_w / 2 + x
        center_y = box_h / 2 + y

        left_top_x = center_x - new_width / 2
        left_top_y = center_y - new_height / 2
        right_bottom_x = center_x + new_width / 2
        right_bottom_y = center_y + new_height / 2

        if left_top_x < 0:
            right_bottom_x -= left_top_x
            left_top_x = 0
        if left_top_y < 0:
            right_bottom_y -= left_top_y
            left_top_y = 0
        if right_bottom_x > src_w - 1:
            left_top_x -= right_bottom_x - src_w + 1
            right_bottom_x = src_w - 1
        if right_bottom_y > src_h - 1:
            left_top_y -= right_bottom_y - src_h + 1
            right_bottom_y = src_h - 1

        return int(left_top_x), int(left_top_y), int(right_bottom_x), int(right_bottom_y)

    def _preprocess_face(self, img_bgr: np.ndarray, bbox_xywh: list, scale: float) -> np.ndarray:
        src_h, src_w = img_bgr.shape[:2]
        lx, ly, rx, ry = self._get_new_box(src_w, src_h, bbox_xywh, scale)
        face_crop = img_bgr[ly:ry + 1, lx:rx + 1]
        face_resized = cv2.resize(face_crop, self.input_size)

        # QUAN TRỌNG: giữ nguyên BGR (không chuyển RGB), KHÔNG chia 255 —
        # đúng y hệt cách model MiniFASNet gốc được train (giá trị pixel [0,255]).
        face_float = face_resized.astype(np.float32)
        face_chw = np.transpose(face_float, (2, 0, 1))
        return np.expand_dims(face_chw, axis=0)

    @staticmethod
    def _parse_scale(model_name: str) -> float:
        parts = model_name.split("_")
        return 4.0 if parts[0] == "4" else float(parts[0])

    def predict(self, img_bgr: np.ndarray, bbox_xyxy) -> Dict:
        """bbox_xyxy: (x1, y1, x2, y2) — lấy TRỰC TIẾP từ ArcFace (best_face.bbox),
        KHÔNG chạy lại face detection riêng bằng Haar Cascade như bản demo gốc —
        tiết kiệm thời gian xử lý vì đã có bbox chính xác từ bước detect trước đó."""
        x1, y1, x2, y2 = [int(v) for v in bbox_xyxy]
        bbox_xywh = [x1, y1, x2 - x1, y2 - y1]

        predictions = np.zeros((1, 3))
        for model_name, session in self.sessions.items():
            scale = self._parse_scale(model_name)
            input_data = self._preprocess_face(img_bgr, bbox_xywh, scale)

            input_name = session.get_inputs()[0].name
            output_name = session.get_outputs()[0].name
            output = session.run([output_name], {input_name: input_data})[0]

            exp_output = np.exp(output - np.max(output, axis=1, keepdims=True))
            softmax_output = exp_output / np.sum(exp_output, axis=1, keepdims=True)
            predictions += softmax_output

        predictions = predictions / len(self.sessions)
        label = int(np.argmax(predictions[0]))
        scores = predictions[0]
        label_names = ["paper", "real", "screen"]

        return {
            "label": label_names[label],
            "is_real": label == 1,
            "scores": {"paper": float(scores[0]), "real": float(scores[1]), "screen": float(scores[2])},
            "confidence": float(scores[label]),
        }
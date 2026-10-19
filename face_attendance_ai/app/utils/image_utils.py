"""
================================================================================
VAI TRÒ FILE NÀY:
Tiện ích thuần kỹ thuật (không phải AI) — chuyển ảnh nhận từ FE (dạng chuỗi
base64, do FE gửi qua JSON) thành mảng ảnh mà OpenCV/InsightFace/MediaPipe
đọc được. Không có nội dung học thuật/nghiên cứu ở đây.
================================================================================
"""

import base64

import cv2
import numpy as np


def base64_to_cv2(base64_str: str) -> np.ndarray:
    """Chuyển chuỗi base64 (có hoặc không có prefix data:image/...) thành ảnh cv2 (BGR)."""
    if "," in base64_str and base64_str.strip().startswith("data:"):
        base64_str = base64_str.split(",", 1)[1]

    try:
        img_bytes = base64.b64decode(base64_str)
    except Exception as exc:  # noqa: BLE001
        raise ValueError("Chuỗi base64 không hợp lệ") from exc

    img_array = np.frombuffer(img_bytes, dtype=np.uint8)
    img = cv2.imdecode(img_array, cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("Không giải mã được ảnh, dữ liệu ảnh không hợp lệ")
    return img


def frame_sharpness(img_bgr: np.ndarray) -> float:
    """Đo độ nét TOÀN ẢNH bằng Laplacian variance — phép đo rẻ (vài ms, không dùng AI),
    dùng để chọn nhanh 1-2 frame nét nhất trong burst TRƯỚC KHI chạy ArcFace (rất tốn
    kém, ~150-250ms/lần). Đây là bước tối ưu tốc độ cho luồng /recognize — xem
    app/api/routes_recognize.py.
    """
    gray = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2GRAY)
    return float(cv2.Laplacian(gray, cv2.CV_64F).var())

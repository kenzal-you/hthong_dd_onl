"""
================================================================================
VAI TRÒ FILE NÀY:
Định nghĩa hình dạng (schema) dữ liệu vào/ra của API bằng Pydantic — FastAPI
dùng để tự validate request và tự sinh docs (Swagger UI tại /docs).
Không có nội dung học thuật/nghiên cứu ở đây, thuần kỹ thuật API.
================================================================================
"""

from typing import List, Optional

from pydantic import BaseModel


class RegisterRequest(BaseModel):
    subjectLabel: str
    frames: List[str]  # burst ảnh base64 liên tiếp (dùng để check liveness + lấy embedding)


class RecognizeRequest(BaseModel):
    frames: List[str]
    threshold: Optional[float] = None  # cho phép override threshold để test độ chính xác


class RegisterResponse(BaseModel):
    success: bool
    message: str
    imageCount: Optional[int] = None
    livenessDetails: Optional[dict] = None  # kết quả Lớp 1 - EAR blink (active liveness)
    textureLiveness: Optional[dict] = None  # kết quả Lớp 2 - SVM/LBP hoặc fallback (passive liveness)


class RecognizeResponse(BaseModel):
    recognized: bool
    isLive: bool
    subjectLabel: Optional[str] = None
    similarity: Optional[float] = None
    livenessDetails: Optional[dict] = None  # kết quả Lớp 1 - EAR blink (active liveness)
    textureLiveness: Optional[dict] = None  # kết quả Lớp 2 - SVM/LBP hoặc fallback (passive liveness)
    latencyMs: Optional[dict] = None


class BBox(BaseModel):
    x: int
    y: int
    w: int
    h: int


class RecognizeLiveRequest(BaseModel):
    """Request cho endpoint xem trực tiếp (live preview) — CHỈ 1 frame, không phải burst."""
    frame: str
    threshold: Optional[float] = None


class RecognizeLiveResponse(BaseModel):
    """Response cho /recognize/live. KHÔNG có trường liveness nào — endpoint này
    không chạy chống giả mạo, chỉ dùng để hiển thị preview, không dùng để quyết định
    điểm danh chính thức (xem app/api/routes_live.py)."""
    faceDetected: bool
    bbox: Optional[BBox] = None
    recognized: bool = False
    subjectLabel: Optional[str] = None
    similarity: Optional[float] = None
    latencyMs: Optional[dict] = None

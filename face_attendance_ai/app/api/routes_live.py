"""
================================================================================
VAI TRÒ FILE NÀY:
Endpoint NHẸ cho chế độ xem trực tiếp (live preview) — nhận 1 frame đơn (không
phải burst), chỉ chạy detect + embedding + matching, KHÔNG chạy liveness (cả 2
lớp) — vì đây chỉ là hiển thị preview real-time (khung bao mặt + tên + độ tin
cậy), KHÔNG dùng để ra quyết định điểm danh chính thức.

THIẾT KẾ 2 TẦNG (2-tier recognition) — ghi vào báo cáo Chương 2 (thiết kế hệ thống):
- Tầng 1 (endpoint này, /recognize/live): chạy liên tục khi camera bật, cho
  UX mượt (thấy khung mặt + tên ngay lập tức), nhưng KHÔNG an toàn để tin cậy
  100% vì bỏ qua chống giả mạo hoàn toàn. Không tạo ra hậu quả gì khi bị "lừa"
  vì không ghi bất cứ gì vào hệ thống.
- Tầng 2 (endpoint /recognize hiện có): chạy burst + đầy đủ 2 lớp liveness,
  dùng để XÁC NHẬN điểm danh chính thức (ghi vào hệ thống) sau khi Tầng 1 đã
  nhận diện ổn định 1 người trong vài giây liên tục.

Đây là mô hình các máy chấm công vân tay/khuôn mặt thương mại (Hikvision,
ZKTeco...) đang dùng: quét liên tục để hiển thị, chỉ xác thực chặt khi thực sự
ghi nhận hành động — cân bằng giữa trải nghiệm người dùng và bảo mật theo
đúng ngữ cảnh sử dụng (use-case-driven security trade-off).
================================================================================
"""

import time

from fastapi import APIRouter

from app.config import settings
from app.core.face_engine import FaceEngine
from app.core.matcher import FaceMatcher
from app.db.database import load_all_embeddings
from app.schemas.models import BBox, RecognizeLiveRequest, RecognizeLiveResponse
from app.utils.image_utils import base64_to_cv2

router = APIRouter()


@router.post("/recognize/live", response_model=RecognizeLiveResponse)
def recognize_live(req: RecognizeLiveRequest):
    t0 = time.time()
    frame = base64_to_cv2(req.frame)

    engine = FaceEngine.instance()
    t1 = time.time()
    face = engine.get_primary_face(frame)
    detect_embed_ms = (time.time() - t1) * 1000

    if face is None:
        return RecognizeLiveResponse(
            faceDetected=False,
            latencyMs={
                "detect_embed_ms": round(detect_embed_ms, 1),
                "total_ms": round((time.time() - t0) * 1000, 1),
            },
        )

    x1, y1, x2, y2 = [int(v) for v in face.bbox]
    bbox = BBox(x=x1, y=y1, w=x2 - x1, h=y2 - y1)

    threshold = req.threshold if req.threshold is not None else settings.RECOGNITION_THRESHOLD
    matcher = FaceMatcher(threshold=threshold)
    gallery = load_all_embeddings()

    t2 = time.time()
    match, score = matcher.match(face.embedding, gallery)
    match_ms = (time.time() - t2) * 1000

    latency = {
        "detect_embed_ms": round(detect_embed_ms, 1),
        "match_ms": round(match_ms, 1),
        "total_ms": round((time.time() - t0) * 1000, 1),
    }

    if match is None:
        return RecognizeLiveResponse(
            faceDetected=True, bbox=bbox, recognized=False, similarity=round(score, 4), latencyMs=latency
        )

    return RecognizeLiveResponse(
        faceDetected=True,
        bbox=bbox,
        recognized=True,
        subjectLabel=match["subject_label"],
        similarity=round(score, 4),
        latencyMs=latency,
    )

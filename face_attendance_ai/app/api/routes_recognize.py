"""
================================================================================
VAI TRÒ FILE NÀY:
API ĐIỂM DANH chính thức (Tầng 2) — nhận vài frame từ FE, điều phối:

    detect + ArcFace -> chống giả mạo bằng CNN (MiniFASNet) -> so khớp gallery

Không còn kiểm tra chớp mắt: phân biệt người thật/giả hoàn toàn do MiniFASNet
(pretrained CNN) đảm nhiệm, chạy trên từng frame nên không cần người dùng làm
động tác nào.

TỐI ƯU TỐC ĐỘ: ArcFace rất tốn (~150-250ms/lần) nên chỉ chạy trên
CANDIDATE_FRAMES frame nét nhất (chọn bằng Laplacian variance, chỉ vài ms).

`threshold` trong request cho phép override RECOGNITION_THRESHOLD để thử nhiều
mức ngưỡng khi đo FAR/FRR mà không cần restart.
================================================================================
"""

import time

from fastapi import APIRouter

from app.config import settings
from app.core.face_engine import FaceEngine
from app.core.matcher import FaceMatcher
from app.core.minifas_engine import MiniFASNetEngine
from app.db.database import load_all_embeddings
from app.schemas.models import RecognizeRequest, RecognizeResponse
from app.utils.image_utils import base64_to_cv2, frame_sharpness
from app.utils.logger import log_latency

router = APIRouter()

# Nạp model ngay khi khởi động: thiếu model CNN thì service báo lỗi rõ ràng,
# thay vì âm thầm chạy với cơ chế chống giả mạo yếu hơn.
anti_spoof = MiniFASNetEngine()

CANDIDATE_FRAMES = 2   # số frame nét nhất được chạy ArcFace + chống giả mạo
MIN_FRAMES = 1


# ------------------------------------------------------------------ helpers
def _elapsed_ms(start: float) -> float:
    return round((time.time() - start) * 1000, 1)


def _with_total(latency: dict, t0: float) -> dict:
    latency["total_ms"] = _elapsed_ms(t0)
    return latency


def _detect_faces(frames) -> list[tuple]:
    """Chọn các frame nét nhất, trả về [(frame, face)] của những frame có mặt."""
    engine = FaceEngine.instance()
    sharpest = sorted(frames, key=frame_sharpness, reverse=True)[:CANDIDATE_FRAMES]

    detected = []
    for frame in sharpest:
        face = engine.get_primary_face(frame)
        if face is not None:
            detected.append((frame, face))
    return detected


def _check_spoof(detected: list[tuple]) -> dict:
    """MiniFASNet: mọi frame ứng viên đều phải là người thật; báo điểm thấp nhất."""
    is_live, min_score = True, 1.0
    for frame, face in detected:
        result = anti_spoof.predict(frame, face.bbox)
        min_score = min(min_score, result["scores"]["real"])
        if not result["is_real"]:
            is_live = False
            break
    return {"is_live": is_live, "score": round(min_score, 4), "method": "minifasnet"}


def _match(embedding, threshold_override: float | None):
    threshold = threshold_override if threshold_override is not None else settings.RECOGNITION_THRESHOLD
    return FaceMatcher(threshold=threshold).match(embedding, load_all_embeddings())


# ------------------------------------------------------------------ endpoint
@router.post("/recognize", response_model=RecognizeResponse)
def recognize_face(req: RecognizeRequest):
    t0 = time.time()
    latency: dict = {}
    details = {"mode": "cnn_only"}

    if not req.frames or len(req.frames) < MIN_FRAMES:
        return RecognizeResponse(
            recognized=False, isLive=False,
            livenessDetails={"reason": f"Cần tối thiểu {MIN_FRAMES} frame"},
        )

    frames = [base64_to_cv2(f) for f in req.frames]

    # ---- Detect + embedding (ArcFace) ----
    t = time.time()
    detected = _detect_faces(frames)
    latency["detect_embed_ms"] = _elapsed_ms(t)

    if not detected:
        return RecognizeResponse(
            recognized=False, isLive=True,
            livenessDetails=details, latencyMs=_with_total(latency, t0),
        )

    # ---- Chống giả mạo bằng CNN ----
    t = time.time()
    spoof = _check_spoof(detected)
    latency["spoof_ms"] = _elapsed_ms(t)

    if not spoof["is_live"]:
        log_latency("recognize_fail_spoof", _with_total(latency, t0))
        return RecognizeResponse(
            recognized=False, isLive=False,
            livenessDetails=details, textureLiveness=spoof, latencyMs=latency,
        )

    # ---- Matching (dùng embedding của mặt có det_score cao nhất) ----
    _, best_face = max(detected, key=lambda pair: pair[1].det_score)

    t = time.time()
    match, score = _match(best_face.embedding, req.threshold)
    latency["match_ms"] = _elapsed_ms(t)

    log_latency("recognize", _with_total(latency, t0))

    return RecognizeResponse(
        recognized=match is not None,
        isLive=True,
        subjectLabel=match["subject_label"] if match else None,
        similarity=round(score, 4),
        livenessDetails=details,
        textureLiveness=spoof,
        latencyMs=latency,
    )
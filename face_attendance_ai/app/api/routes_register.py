"""
================================================================================
VAI TRÒ FILE NÀY:
Tầng API (HTTP layer) cho việc ĐĂNG KÝ khuôn mặt (enrollment) — nhận request
từ FE, điều phối gọi lần lượt: liveness Lớp 1 (blink) -> trích embedding ->
liveness Lớp 2 (texture/SVM) trên chính frame đã chọn -> lưu MySQL.
File này KHÔNG chứa logic AI, chỉ "lắp ráp" các module trong app/core/.

HỌC THUẬT / NGHIÊN CỨU Ở ĐÂY:
Thứ tự gọi có chủ đích:
  1) Blink check trước — lọc nhanh, rẻ, chặn sớm ảnh tĩnh/không hợp tác.
  2) Sau khi có bbox khuôn mặt từ ArcFace, mới chạy texture/SVM check — vì
     lớp này cần bbox chính xác để crop đúng vùng da mặt, không thể chạy
     trước bước detect.
  3) Chỉ khi CẢ HAI lớp liveness đều pass mới lưu embedding vào gallery —
     nếu enrollment bị giả mạo lọt qua, mọi lần nhận diện sau đều sai theo,
     nên đây là bước cần chặt chẽ nhất trong toàn bộ pipeline.
================================================================================
"""

import time

from fastapi import APIRouter, HTTPException

from app.config import settings
from app.core.face_engine import FaceEngine
from app.core.liveness import LivenessChecker
from app.db.database import count_subject, save_embedding
from app.schemas.models import RegisterRequest, RegisterResponse
from app.utils.image_utils import base64_to_cv2
from app.utils.logger import log_latency
from app.db.database import count_subject, replace_embedding

router = APIRouter()
liveness_checker = LivenessChecker()


@router.post("/register", response_model=RegisterResponse)
def register_face(req: RegisterRequest):
    t0 = time.time()

    if not req.frames or len(req.frames) < settings.LIVENESS_MIN_FRAMES:
        raise HTTPException(
            status_code=400,
            detail=f"Cần gửi tối thiểu {settings.LIVENESS_MIN_FRAMES} frame liên tiếp để kiểm tra liveness",
        )

    frames = [base64_to_cv2(f) for f in req.frames]

    # ---- Lớp 1: Active liveness (blink) ----
    t1 = time.time()
    is_live, liveness_details = liveness_checker.check_blink_sequence(frames)
    liveness_ms = (time.time() - t1) * 1000

    if not is_live:
        log_latency(
            "register_fail_liveness",
            {"liveness_ms": liveness_ms, "total_ms": (time.time() - t0) * 1000},
        )
        return RegisterResponse(
            success=False,
            message="Không xác thực được liveness (chưa phát hiện chớp mắt hợp lệ). Vui lòng nhìn thẳng camera và chớp mắt tự nhiên.",
            livenessDetails=liveness_details,
        )

    # ---- Detect + Embedding (ArcFace) ----
    engine = FaceEngine.instance()
    t2 = time.time()
    best_face, best_frame, best_score = None, None, -1.0
    for frame in frames:
        face = engine.get_primary_face(frame)
        if face is not None and face.det_score > best_score:
            best_face, best_frame, best_score = face, frame, face.det_score
    detect_embed_ms = (time.time() - t2) * 1000

    if best_face is None:
        log_latency(
            "register_fail_no_face",
            {
                "liveness_ms": liveness_ms,
                "detect_embed_ms": detect_embed_ms,
                "total_ms": (time.time() - t0) * 1000,
            },
        )
        raise HTTPException(status_code=400, detail="Không phát hiện được khuôn mặt rõ trong burst ảnh")

    # ---- Lớp 2: Passive liveness (texture/SVM), chạy trên frame vừa chọn ----
    t2b = time.time()
    texture_is_live, texture_score, texture_method = liveness_checker.texture_ml_check(
        best_frame, best_face.bbox
    )
    texture_ms = (time.time() - t2b) * 1000
    texture_details = {"is_live": texture_is_live, "score": round(texture_score, 4), "method": texture_method}

    if not texture_is_live:
        log_latency(
            "register_fail_texture",
            {
                "liveness_ms": liveness_ms,
                "detect_embed_ms": detect_embed_ms,
                "total_ms": (time.time() - t0) * 1000,
            },
        )
        return RegisterResponse(
            success=False,
            message="Vượt qua kiểm tra chớp mắt nhưng nghi vấn ảnh giả mạo (texture bất thường). Vui lòng thử lại với camera trực tiếp.",
            livenessDetails=liveness_details,
            textureLiveness=texture_details,
        )

    replace_embedding(req.subjectLabel, best_face.embedding, best_face.det_score)

    total_ms = (time.time() - t0) * 1000
    log_latency(
        "register_success",
        {
            "liveness_ms": liveness_ms,
            "detect_embed_ms": detect_embed_ms + texture_ms,
            "total_ms": total_ms,
        },
    )

    count = count_subject(req.subjectLabel)
    return RegisterResponse(
        success=True,
        message=f"Đã đăng ký khuôn mặt cho subject '{req.subjectLabel}'",
        imageCount=count,
        livenessDetails=liveness_details,
        textureLiveness=texture_details,
    )


@router.delete("/subject/{subject_label}")
def delete_subject_endpoint(subject_label: str):
    """Xoá toàn bộ embedding của 1 subject.

    QUAN TRỌNG: LUÔN dùng endpoint này để xoá, KHÔNG xoá tay bằng SQL trực tiếp
    trên MySQL — vì database.delete_subject() sẽ tự động làm mới (invalidate)
    cache gallery trong RAM. Xoá tay bằng SQL sẽ khiến cache cũ vẫn còn, dẫn đến
    tình trạng "đã xoá DB nhưng /recognize vẫn nhận diện được người đã xoá" cho
    đến khi restart server thủ công.
    """
    from app.db.database import delete_subject

    delete_subject(subject_label)
    return {"message": f"Đã xoá subject '{subject_label}'"}

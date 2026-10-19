"""
================================================================================
VAI TRÒ FILE NÀY:
Bước RA QUYẾT ĐỊNH cuối cùng: so sánh embedding vừa trích được với toàn bộ
embedding đã đăng ký (gallery), tìm người giống nhất và quyết định có "nhận"
hay "từ chối" dựa trên threshold.

HỌC THUẬT / NGHIÊN CỨU Ở ĐÂY:
Độ đo: Cosine Similarity giữa 2 vector 512D (đặc trưng ArcFace).
similarity càng gần 1 -> càng giống nhau. Đây KHÔNG phải model, chỉ là công
thức toán (tích vô hướng 2 vector đã chuẩn hoá) — không có gì để "train".

RECOGNITION_THRESHOLD (app/config.py) chính là tham số "điều chỉnh độ chính xác":
- Tăng threshold -> hệ thống khó chấp nhận hơn -> FAR giảm, FRR tăng
- Giảm threshold -> hệ thống dễ chấp nhận hơn -> FAR tăng, FRR giảm
Đây là cơ sở để bạn vẽ đường cong ROC / tìm điểm cân bằng EER cho Chương 4.
"""

import numpy as np


def cosine_similarity(a: np.ndarray, b: np.ndarray) -> float:
    a_norm = a / (np.linalg.norm(a) + 1e-10)
    b_norm = b / (np.linalg.norm(b) + 1e-10)
    return float(np.dot(a_norm, b_norm))


class FaceMatcher:
    def __init__(self, threshold: float) -> None:
        self.threshold = threshold

    def match(self, query_embedding: np.ndarray, gallery: list[dict]):
        """
        gallery: list các dict {"subject_label": str, "embedding": np.ndarray}
        Trả về (best_entry hoặc None, best_score)
        """
        best_entry = None
        best_score = -1.0
        for entry in gallery:
            score = cosine_similarity(query_embedding, entry["embedding"])
            if score > best_score:
                best_score = score
                best_entry = entry

        if best_entry is not None and best_score >= self.threshold:
            return best_entry, best_score
        return None, best_score

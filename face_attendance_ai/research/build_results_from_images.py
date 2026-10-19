"""
================================================================================
VAI TRÒ FILE NÀY:
Sinh file results.csv để evaluate_metrics.py phân tích, bằng cách chạy nhận
diện trên ẢNH TĨNH (không qua webcam, không qua liveness) — phù hợp để đánh
giá THUẦN TÚY khả năng nhận diện của ArcFace + threshold, tách biệt khỏi yếu
tố liveness (vì liveness cần video, không đánh giá được qua ảnh tĩnh đơn lẻ).

CHUẨN BỊ DỮ LIỆU:
  data/eval_dataset/
    genuine/    <- ảnh của các sinh viên ĐÃ đăng ký (đã có embedding trong MySQL),
                    chụp thêm ở buổi khác/góc khác để test "người quen, ảnh mới"
    impostor/   <- ảnh của người KHÔNG có trong hệ thống (bạn bè chưa đăng ký,
                    hoặc tập ảnh công khai như LFW subset)

Mỗi ảnh nên là 1 khuôn mặt tương đối rõ, tên file tuỳ ý (.jpg/.png).
Khuyến nghị: >= 50 ảnh mỗi loại để số liệu FAR/FRR có ý nghĩa thống kê.

CÁCH DÙNG:
    python research/build_results_from_images.py
    -> sinh ra research/results.csv
    -> chạy tiếp: python research/evaluate_metrics.py
================================================================================
"""

import glob
import os
import sys

import cv2
import pandas as pd

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from app.core.face_engine import FaceEngine  # noqa: E402
from app.core.matcher import cosine_similarity  # noqa: E402
from app.db.database import load_all_embeddings  # noqa: E402

DATA_DIR = os.path.join("data", "eval_dataset")
OUT_CSV = os.path.join(os.path.dirname(__file__), "results.csv")


def best_similarity(embedding, gallery) -> float:
    """Điểm similarity cao nhất giữa 1 embedding và toàn bộ gallery đã đăng ký
    (mô phỏng đúng logic matcher.py dùng trong /recognize)."""
    if not gallery:
        return 0.0
    return max(cosine_similarity(embedding, g["embedding"]) for g in gallery)


def main() -> None:
    engine = FaceEngine.instance()
    gallery = load_all_embeddings()
    if not gallery:
        print("Gallery rỗng — cần đăng ký ít nhất 1 sinh viên qua /register trước khi chạy đánh giá.")
        return

    print(f"Gallery hiện có {len(gallery)} embedding đã đăng ký.")

    rows = []
    for trial_type in ("genuine", "impostor"):
        folder = os.path.join(DATA_DIR, trial_type)
        paths = sorted(
            glob.glob(os.path.join(folder, "*.jpg")) + glob.glob(os.path.join(folder, "*.png"))
        )
        print(f"[{trial_type}] tìm thấy {len(paths)} ảnh trong {folder}")
        for path in paths:
            img = cv2.imread(path)
            if img is None:
                print(f"  Bỏ qua {path} (đọc ảnh lỗi)")
                continue
            face = engine.get_primary_face(img)
            if face is None:
                print(f"  Bỏ qua {path} (không phát hiện khuôn mặt)")
                continue
            sim = best_similarity(face.embedding, gallery)
            rows.append({"file": path, "trial_type": trial_type, "similarity": round(sim, 6)})

    if not rows:
        print("Không thu được dòng dữ liệu nào — kiểm tra lại thư mục data/eval_dataset/")
        return

    df = pd.DataFrame(rows)
    df.to_csv(OUT_CSV, index=False)
    print(f"\nĐã lưu {len(df)} dòng vào {OUT_CSV}")
    print("Chạy tiếp: python research/evaluate_metrics.py")


if __name__ == "__main__":
    main()

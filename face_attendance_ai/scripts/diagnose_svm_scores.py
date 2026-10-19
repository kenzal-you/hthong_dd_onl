"""
================================================================================
VAI TRO FILE NAY:
Cong cu CHAN DOAN - chup lien tuc N lan tu webcam, tinh diem SVM (texture_ml_check)
cho tung lan, in ra bang thong ke (min/max/trung binh/do lech chuan). Dung khi
threshold 0.5 mac dinh co ve khong phu hop, muon biet diem that cua khuon mat
BAN dao dong trong khoang nao truoc khi chinh threshold, thay vi doan mo.

CACH DUNG:
    python scripts/diagnose_svm_scores.py

Ngoi truoc webcam, giu nguyen tu the binh thuong (dieu kien anh sang thuc te
se dung), chuong trinh se tu dong chup va tinh diem N lan lien tiep (khong can
nhan phim gi giua cac lan chup).
================================================================================
"""

import os
import sys
import time

import cv2
import numpy as np

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from app.core.face_engine import FaceEngine  # noqa: E402
from app.core.liveness import LivenessChecker  # noqa: E402

NUM_TRIALS = 15
DELAY_BETWEEN = 0.3  # giay giua moi lan chup


def main() -> None:
    print("Dang load model...")
    engine = FaceEngine.instance()
    liveness = LivenessChecker()

    cap = cv2.VideoCapture(0, cv2.CAP_DSHOW)
    if not cap.isOpened():
        cap = cv2.VideoCapture(0)

    if not cap.isOpened():
        print("Khong mo duoc webcam.")
        return

    print(f"Se chup {NUM_TRIALS} lan lien tiep, giu nguyen tu the binh thuong...")
    time.sleep(1)

    scores = []
    for i in range(NUM_TRIALS):
        ok, frame = cap.read()
        if not ok:
            print(f"Lan {i+1}: khong doc duoc frame, bo qua.")
            continue

        face = engine.get_primary_face(frame)
        if face is None:
            print(f"Lan {i+1}: khong phat hien khuon mat, bo qua.")
            time.sleep(DELAY_BETWEEN)
            continue

        is_live, score, method = liveness.texture_ml_check(frame, face.bbox)
        scores.append(score)
        print(f"Lan {i+1:2d}: score={score:.4f}  method={method}  is_live={is_live}")
        time.sleep(DELAY_BETWEEN)

    cap.release()

    if not scores:
        print("\nKhong thu duoc diem nao. Kiem tra lai webcam/anh sang.")
        return

    scores_arr = np.array(scores)
    print("\n=== THONG KE ===")
    print(f"So lan thu:      {len(scores_arr)}")
    print(f"Min:             {scores_arr.min():.4f}")
    print(f"Max:             {scores_arr.max():.4f}")
    print(f"Trung binh:      {scores_arr.mean():.4f}")
    print(f"Do lech chuan:   {scores_arr.std():.4f}")

    suggested = max(0.05, scores_arr.mean() - 2 * scores_arr.std())
    print(f"\nGoi y threshold (trung binh - 2*do lech chuan): {suggested:.4f}")
    print(
        "Luu y: day chi la goi y dua tren KHUON MAT THAT cua ban trong dieu kien "
        "vua chup. Threshold qua thap co the lam giam kha nang chan anh gia - "
        "can can nhac dung hoa hop ly, khong chi lay dung so nay."
    )


if __name__ == "__main__":
    main()

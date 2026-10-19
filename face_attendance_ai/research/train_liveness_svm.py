"""
================================================================================
VAI TRÒ FILE NÀY:
Script HUẤN LUYỆN (offline, chạy tay 1 lần, KHÔNG chạy trong service lúc
production) một classifier SVM nhị phân real/spoof, dựa trên đặc trưng LBP
(app/core/texture_features.py).

ĐÂY LÀ PHẦN "TRAINING" THẬT SỰ CỦA ĐỒ ÁN:
Khác với ArcFace/MediaPipe (pretrained, không đụng vào), model SVM này DO CHÍNH
BẠN huấn luyện trên dữ liệu tự thu thập — có train/test split, có báo cáo
precision/recall/F1, có thể ghi hẳn 1 mục trong báo cáo Chương 3 về quy trình
xây dựng bộ dữ liệu + huấn luyện + đánh giá model này.

Vì sao chọn SVM (thay vì train 1 mạng CNN từ đầu):
- Dữ liệu tự thu thập thường chỉ vài trăm ảnh -> CNN train từ đầu dễ overfit
  nặng, cần vài nghìn-chục nghìn ảnh mới ổn định.
- SVM trên đặc trưng thủ công (LBP) là baseline chuẩn trong các nghiên cứu
  anti-spoofing giai đoạn 2011-2016 (trước khi deep learning phổ biến cho
  bài toán này), phù hợp quy mô dữ liệu sinh viên tự thu thập, vẫn có cơ sở
  học thuật rõ ràng để trích dẫn.

CÁCH DÙNG:
1. Chuẩn bị dữ liệu bằng collect_liveness_samples.py:
     data/liveness_dataset/real/*.jpg
     data/liveness_dataset/spoof/*.jpg
2. Chạy:  python research/train_liveness_svm.py
3. Model lưu tại models/liveness_svm.pkl — app/core/liveness.py sẽ tự động
   load model này (nếu tồn tại) để làm lớp liveness bổ trợ (passive liveness).
================================================================================
"""

import glob
import os
import sys

import cv2
import joblib
import numpy as np
from sklearn.metrics import classification_report
from sklearn.model_selection import train_test_split
from sklearn.svm import SVC

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from app.core.texture_features import extract_lbp_histogram  # noqa: E402

DATA_DIR = os.path.join("data", "liveness_dataset")
MODEL_PATH = os.path.join("models", "liveness_svm.pkl")
MIN_SAMPLES_PER_CLASS = 20  # ngưỡng tối thiểu để SVM học có ý nghĩa (khuyến nghị thực tế >=150)


def load_dataset():
    X, y = [], []
    for label_value, folder_name in [(1, "real"), (0, "spoof")]:
        paths = sorted(
            glob.glob(os.path.join(DATA_DIR, folder_name, "*.jpg"))
            + glob.glob(os.path.join(DATA_DIR, folder_name, "*.png"))
        )
        print(f"[{folder_name}] tìm thấy {len(paths)} ảnh")
        for path in paths:
            img = cv2.imread(path)
            if img is None:
                continue
            X.append(extract_lbp_histogram(img))
            y.append(label_value)
    return np.array(X), np.array(y)


def main() -> None:
    X, y = load_dataset()

    n_real = int((y == 1).sum())
    n_spoof = int((y == 0).sum())
    if n_real < MIN_SAMPLES_PER_CLASS or n_spoof < MIN_SAMPLES_PER_CLASS:
        print(
            f"\nDữ liệu chưa đủ (real={n_real}, spoof={n_spoof}, cần tối thiểu "
            f"{MIN_SAMPLES_PER_CLASS} mỗi lớp). Chạy collect_liveness_samples.py để thu thập thêm."
        )
        return

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, stratify=y, random_state=42
    )

    # SVM kernel RBF - chuẩn cho bài toán phân loại nhị phân với đặc trưng thủ công,
    # class_weight="balanced" để không thiên vị nếu 2 lớp lệch số lượng mẫu.
    clf = SVC(kernel="rbf", C=1.0, gamma="scale", probability=True, class_weight="balanced")
    clf.fit(X_train, y_train)

    y_pred = clf.predict(X_test)
    print("\n=== Kết quả đánh giá trên tập test (giữ lại 20% dữ liệu) ===")
    print(classification_report(y_test, y_pred, target_names=["spoof", "real"]))
    print("^ Bảng này copy thẳng được vào báo cáo Chương 4 (đánh giá module liveness).")

    os.makedirs("models", exist_ok=True)
    joblib.dump(clf, MODEL_PATH)
    print(f"\nĐã lưu model tại {MODEL_PATH}")


if __name__ == "__main__":
    main()

"""
================================================================================
VAI TRÒ FILE NÀY:
Script phân tích OFFLINE (không chạy trong service, chạy tay khi cần làm báo
cáo), dùng để tính các chỉ số đánh giá học thuật cho Chương 4: Accuracy,
Precision, Recall, F1, FAR, FRR, và tìm EER (Equal Error Rate) bằng cách quét
nhiều threshold khác nhau.

HỌC THUẬT / NGHIÊN CỨU Ở ĐÂY:
Đây là phương pháp luận đánh giá chuẩn trong các nghiên cứu sinh trắc học
(biometrics): với mỗi threshold, hệ thống có 2 loại lỗi đối lập nhau:
  - FAR (False Acceptance Rate): tỉ lệ chấp nhận NHẦM người lạ
  - FRR (False Rejection Rate): tỉ lệ từ chối NHẦM người đã đăng ký
Hai chỉ số này luôn TRADE-OFF với nhau khi đổi threshold — đây chính là dữ
liệu thực nghiệm chứng minh cho tham số "threshold điều chỉnh được" mà đề bài
yêu cầu. Điểm mà FAR = FRR gọi là EER (Equal Error Rate) — chỉ số tổng hợp
phổ biến nhất để so sánh độ mạnh của các hệ thống sinh trắc học với nhau.

CÁCH DÙNG:
1. Sinh file research/results.csv bằng build_results_from_images.py
   (hoặc tự tạo tay CSV có 2 cột: trial_type [genuine/impostor], similarity)
2. Chạy: python research/evaluate_metrics.py
   -> in bảng Accuracy/Precision/Recall/F1/FAR/FRR tại threshold hiện tại
   -> quét threshold 0.0 -> 1.0, tìm điểm EER
   -> lưu biểu đồ đường cong FAR/FRR vào research/far_frr_curve.png
================================================================================
"""

import os
import sys

import matplotlib

matplotlib.use("Agg")  # không cần màn hình hiển thị, chỉ xuất file ảnh
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from sklearn.metrics import accuracy_score, f1_score, precision_score, recall_score

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from app.config import settings  # noqa: E402

RESULTS_CSV = os.path.join(os.path.dirname(__file__), "results.csv")
CURVE_PNG = os.path.join(os.path.dirname(__file__), "far_frr_curve.png")


def load_results() -> pd.DataFrame:
    if not os.path.exists(RESULTS_CSV):
        raise FileNotFoundError(
            f"Không tìm thấy {RESULTS_CSV}. Chạy build_results_from_images.py trước, "
            "hoặc tự tạo CSV có cột trial_type,similarity."
        )
    df = pd.read_csv(RESULTS_CSV)
    if not {"trial_type", "similarity"}.issubset(df.columns):
        raise ValueError("File CSV cần có tối thiểu 2 cột: trial_type, similarity")
    df["ground_truth"] = (df["trial_type"] == "genuine").astype(int)
    return df


def metrics_at_threshold(df: pd.DataFrame, threshold: float) -> dict:
    pred = (df["similarity"] >= threshold).astype(int)
    y = df["ground_truth"]

    genuine = df[df["ground_truth"] == 1]
    impostor = df[df["ground_truth"] == 0]

    frr = float((genuine["similarity"] < threshold).mean()) if len(genuine) else float("nan")
    far = float((impostor["similarity"] >= threshold).mean()) if len(impostor) else float("nan")

    return {
        "threshold": threshold,
        "accuracy": accuracy_score(y, pred),
        "precision": precision_score(y, pred, zero_division=0),
        "recall": recall_score(y, pred, zero_division=0),
        "f1": f1_score(y, pred, zero_division=0),
        "FAR": far,
        "FRR": frr,
    }


def sweep_thresholds(df: pd.DataFrame, steps: int = 101) -> pd.DataFrame:
    thresholds = np.linspace(0, 1, steps)
    return pd.DataFrame([metrics_at_threshold(df, t) for t in thresholds])


def find_eer(sweep_df: pd.DataFrame):
    diff = (sweep_df["FAR"] - sweep_df["FRR"]).abs()
    idx = diff.idxmin()
    row = sweep_df.loc[idx]
    eer = (row["FAR"] + row["FRR"]) / 2
    return float(row["threshold"]), float(eer)


def main() -> None:
    df = load_results()
    n_genuine = int((df["ground_truth"] == 1).sum())
    n_impostor = int((df["ground_truth"] == 0).sum())
    print(f"Đã nạp {len(df)} lượt thử ({n_genuine} genuine, {n_impostor} impostor)\n")

    print(f"=== Kết quả tại threshold hiện tại của hệ thống ({settings.RECOGNITION_THRESHOLD}) ===")
    current = metrics_at_threshold(df, settings.RECOGNITION_THRESHOLD)
    for k, v in current.items():
        print(f"{k:>10}: {v:.4f}" if isinstance(v, float) else f"{k:>10}: {v}")

    sweep_df = sweep_thresholds(df)
    eer_threshold, eer = find_eer(sweep_df)
    print("\n=== Equal Error Rate (EER) — điểm cân bằng FAR/FRR ===")
    print(f"Threshold tối ưu (EER point): {eer_threshold:.3f}")
    print(f"EER: {eer:.4f}")
    print(
        "\n(Có thể cân nhắc đổi RECOGNITION_THRESHOLD trong app/config.py sang giá trị "
        "gần điểm EER nếu muốn cân bằng giữa 2 loại lỗi cho hệ thống thật.)"
    )

    plt.figure(figsize=(7, 5))
    plt.plot(sweep_df["threshold"], sweep_df["FAR"], label="FAR (False Acceptance Rate)")
    plt.plot(sweep_df["threshold"], sweep_df["FRR"], label="FRR (False Rejection Rate)")
    plt.axvline(eer_threshold, color="red", linestyle="--", label=f"EER threshold ≈ {eer_threshold:.3f}")
    plt.xlabel("Threshold (cosine similarity)")
    plt.ylabel("Tỉ lệ lỗi")
    plt.title("Đường cong FAR / FRR theo Threshold")
    plt.legend()
    plt.grid(alpha=0.3)
    plt.savefig(CURVE_PNG, dpi=150, bbox_inches="tight")
    print(f"\nĐã lưu biểu đồ tại: {CURVE_PNG}  (chèn thẳng vào Chương 4 của báo cáo)")


if __name__ == "__main__":
    main()

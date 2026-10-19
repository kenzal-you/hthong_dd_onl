"""
================================================================================
VAI TRÒ FILE NÀY:
Ghi lại thời gian xử lý (ms) của từng bước (liveness, detect+embedding, matching)
ra file CSV mỗi lần có request /register hoặc /recognize.

HỌC THUẬT / NGHIÊN CỨU Ở ĐÂY:
Đây chính là nguồn dữ liệu thô để tính chỉ số "Tốc độ xử lý (FPS/Latency)"
trong Chương 4 — không phải chạy 1 lần đo tay, mà tích lũy qua nhiều lượt
thực nghiệm thật rồi tính trung bình/vẽ biểu đồ, thuyết phục hơn cho báo cáo.
================================================================================
"""

import csv
import os
from datetime import datetime

from app.config import settings


def log_latency(event: str, stages: dict) -> None:
    """Ghi lại thời gian xử lý từng giai đoạn vào file CSV để sau này tính FPS/Latency trung bình.

    Chạy nhiều lần (vd. 100-200 lượt điểm danh thử nghiệm) rồi mở file logs/latency_log.csv
    bằng pandas/Excel để tính trung bình, vẽ biểu đồ cho Chương 4.
    """
    os.makedirs(settings.LOG_DIR, exist_ok=True)
    path = os.path.join(settings.LOG_DIR, "latency_log.csv")
    file_exists = os.path.exists(path)

    with open(path, "a", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        if not file_exists:
            writer.writerow(
                ["timestamp", "event", "liveness_ms", "detect_embed_ms", "match_ms", "total_ms"]
            )
        writer.writerow(
            [
                datetime.now().isoformat(timespec="seconds"),
                event,
                stages.get("liveness_ms"),
                stages.get("detect_embed_ms"),
                stages.get("match_ms"),
                stages.get("total_ms"),
            ]
        )

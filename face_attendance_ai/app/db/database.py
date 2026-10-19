"""
================================================================================
VAI TRÒ FILE NÀY:
Lớp lưu trữ dữ liệu (Data Access Layer) cho vector embedding khuôn mặt,
dùng MySQL — cùng loại CSDL với hệ thống Spring Boot chính, để dễ đồng bộ
(hoặc Spring Boot có thể đọc chéo bảng này nếu cần báo cáo/thống kê).

HỌC THUẬT / NGHIÊN CỨU Ở ĐÂY:
Về bản chất đây KHÔNG phải phần "AI" — đây là bước kỹ thuật lưu trữ.
Điểm đáng nói cho báo cáo (Chương 2 - phân tích thiết kế) là lựa chọn cách
lưu vector 512D trong MySQL:
  - Dùng kiểu cột JSON để lưu mảng 512 số thực (đơn giản, đủ dùng cho quy mô
    vài trăm - vài nghìn sinh viên vì việc so khớp vẫn chạy tuần tự trên RAM
    sau khi load toàn bộ gallery — xem app/core/matcher.py).
  - Nếu quy mô lớn hơn (hàng chục nghìn người, cần tốc độ tra cứu cao), hướng
    nghiên cứu mở rộng là dùng Vector Database (Milvus, pgvector, FAISS index)
    hỗ trợ Approximate Nearest Neighbor (ANN) search thay vì so khớp tuần tự.
    Đây là điểm có thể ghi vào phần "Hướng phát triển" của báo cáo.
================================================================================
"""

import json

import numpy as np
import pymysql
from pymysql.cursors import DictCursor

from app.config import settings

# Cache gallery trong RAM (tối ưu tốc độ): tránh phải query MySQL mỗi lần /recognize.
# Chỉ nạp lại khi có thay đổi (đăng ký mới / xoá) — xem _invalidate_cache().
# LƯU Ý (ghi vào báo cáo phần hạn chế): cache này chỉ đúng khi chạy 1 tiến trình
# uvicorn duy nhất (--workers 1, mặc định). Nếu chạy nhiều worker process sau này,
# cần chuyển sang cache dùng chung (Redis) để tránh dữ liệu lệch nhau giữa các process.
_gallery_cache: list[dict] | None = None


def _invalidate_cache() -> None:
    global _gallery_cache
    _gallery_cache = None


def get_conn():
    """Mở kết nối tới MySQL và đảm bảo bảng face_embeddings đã tồn tại."""
    conn = pymysql.connect(
        host=settings.MYSQL_HOST,
        port=settings.MYSQL_PORT,
        user=settings.MYSQL_USER,
        password=settings.MYSQL_PASSWORD,
        database=settings.MYSQL_DATABASE,
        charset="utf8mb4",
        cursorclass=DictCursor,
    )
    with conn.cursor() as cur:
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS face_embeddings (
                id INT AUTO_INCREMENT PRIMARY KEY,
                subject_label VARCHAR(100) NOT NULL,
                embedding JSON NOT NULL,
                det_score FLOAT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_subject_label (subject_label)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """
        )
    conn.commit()
    return conn


def save_embedding(subject_label: str, embedding: np.ndarray, det_score: float) -> None:
    """Lưu 1 vector embedding (bước ENROLLMENT, không phải training - xem README)."""
    conn = get_conn()
    try:
        with conn.cursor() as cur:
            cur.execute(
                "INSERT INTO face_embeddings (subject_label, embedding, det_score) "
                "VALUES (%s, %s, %s)",
                (subject_label, json.dumps(embedding.tolist()), float(det_score)),
            )
        conn.commit()
    finally:
        conn.close()
    _invalidate_cache()  # có dữ liệu mới -> lần load_all_embeddings() tiếp theo phải nạp lại từ MySQL

def replace_embedding(subject_label: str, embedding: np.ndarray, det_score: float) -> None:
    """Thay thế embedding của subject: xoá hết bản cũ rồi chèn bản mới,
    trong CÙNG 1 transaction (lỗi giữa chừng thì không mất dữ liệu cũ)."""
    conn = get_conn()
    try:
        with conn.cursor() as cur:
            cur.execute("DELETE FROM face_embeddings WHERE subject_label = %s", (subject_label,))
            cur.execute(
                "INSERT INTO face_embeddings (subject_label, embedding, det_score) "
                "VALUES (%s, %s, %s)",
                (subject_label, json.dumps(embedding.tolist()), float(det_score)),
            )
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()
    _invalidate_cache()

def load_all_embeddings() -> list[dict]:
    """Tải toàn bộ gallery embedding vào RAM để matcher.py so khớp cosine similarity.
    TỐI ƯU: kết quả được cache trong RAM (_gallery_cache), chỉ query MySQL khi
    cache trống (lần đầu, hoặc sau khi có đăng ký/xoá mới)."""
    global _gallery_cache
    if _gallery_cache is not None:
        return _gallery_cache

    conn = get_conn()
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT subject_label, embedding FROM face_embeddings")
            rows = cur.fetchall()
    finally:
        conn.close()

    gallery = []
    for row in rows:
        embedding_raw = row["embedding"]
        # pymysql trả cột JSON dưới dạng str, cần json.loads; một số driver có thể
        # tự parse sẵn thành list -> kiểm tra type để tương thích cả hai trường hợp
        embedding_list = json.loads(embedding_raw) if isinstance(embedding_raw, str) else embedding_raw
        gallery.append(
            {
                "subject_label": row["subject_label"],
                "embedding": np.array(embedding_list, dtype=np.float32),
            }
        )
    _gallery_cache = gallery
    return gallery


def count_subject(subject_label: str) -> int:
    conn = get_conn()
    try:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT COUNT(*) AS cnt FROM face_embeddings WHERE subject_label = %s",
                (subject_label,),
            )
            row = cur.fetchone()
            return row["cnt"] if row else 0
    finally:
        conn.close()


def delete_subject(subject_label: str) -> None:
    conn = get_conn()
    try:
        with conn.cursor() as cur:
            cur.execute("DELETE FROM face_embeddings WHERE subject_label = %s", (subject_label,))
        conn.commit()
    finally:
        conn.close()
    _invalidate_cache()

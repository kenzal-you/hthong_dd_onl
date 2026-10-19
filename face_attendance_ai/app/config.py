"""
================================================================================
VAI TRÒ FILE NÀY:
Nơi khai báo TẤT CẢ tham số có thể chỉnh (threshold, thông tin kết nối MySQL...)
ở một chỗ duy nhất, đọc qua biến môi trường (os.getenv) để khi deploy không cần
sửa code, chỉ cần set biến môi trường (hoặc file .env).

HỌC THUẬT / NGHIÊN CỨU Ở ĐÂY:
- RECOGNITION_THRESHOLD chính là "nút vặn độ chính xác" mà đề bài yêu cầu.
  Đây KHÔNG phải tham số của model (model không đổi), mà là tham số của bước
  RA QUYẾT ĐỊNH sau khi đã có điểm similarity. Đổi số này để thực nghiệm và
  vẽ đường cong FAR/FRR cho Chương 4 (xem README mục 7).
- Các tham số LIVENESS_* là tham số của thuật toán EAR blink detection
  (không phải của một model học sâu nào) — đây là thuật toán dựa trên
  hình học landmark, không có "trọng số" để train.
================================================================================
"""

import os


class Settings:
    # ==== Kết nối MySQL ====
    # Dùng CHUNG 1 database MySQL với hệ thống Spring Boot (khác bảng),
    # hoặc tạo database riêng cho service AI đều được — chỉnh qua biến môi trường.
    MYSQL_HOST = os.getenv("MYSQL_HOST", "localhost")
    MYSQL_PORT = int(os.getenv("MYSQL_PORT", 3306))
    MYSQL_USER = os.getenv("MYSQL_USER", "root")
    MYSQL_PASSWORD = os.getenv("MYSQL_PASSWORD", "123456")
    MYSQL_DATABASE = os.getenv("MYSQL_DATABASE", "face_attendance_db")
    INTERNAL_API_KEY = os.getenv(
    "INTERNAL_API_KEY",
    "fa7c1e9b4d2a8f60c3b5e1d97a40826fb3c9d15e7a28f4b60c1d3e5a79b2f84c",
)
    LIVENESS_BLINK_ENABLED: bool = False
    # ==== Nhận diện (Recognition) ====
    # Ngưỡng cosine similarity để coi là "trùng khớp".
    # Càng CAO -> hệ thống càng khó tính -> FAR giảm, FRR tăng.
    # Càng THẤP -> hệ thống càng dễ tính -> FAR tăng, FRR giảm.
    # ĐÂY LÀ THAM SỐ THỰC NGHIỆM CHÍNH cho Chương 4 (không phải trọng số model).
    RECOGNITION_THRESHOLD = float(os.getenv("RECOGNITION_THRESHOLD", 0.42))

    # Model embedding dùng sẵn (pretrained) từ InsightFace, KHÔNG train lại.
    INSIGHTFACE_MODEL_NAME = os.getenv("INSIGHTFACE_MODEL_NAME", "buffalo_l")
    # Hạ từ (640,640) xuống (320,320) để tối ưu tốc độ /recognize — khuôn mặt
    # webcam thường chiếm phần lớn khung hình nên không cần độ phân giải detect cao.
    DET_SIZE = (320, 320)

    # ==== Liveness (chống giả mạo) ====
    # EAR (Eye Aspect Ratio) dưới ngưỡng này coi là mắt đang nhắm.
    # Đây là ngưỡng hình học (dựa trên landmark), KHÔNG phải tham số model.
    LIVENESS_EAR_THRESHOLD = float(os.getenv("LIVENESS_EAR_THRESHOLD", 0.21))
    # Số frame tối thiểu cần gửi lên để phân tích chuỗi chớp mắt
    LIVENESS_MIN_FRAMES = int(os.getenv("LIVENESS_MIN_FRAMES", 5))
    # Ngưỡng phụ: độ nét bề mặt (Laplacian variance) - hỗ trợ phát hiện ảnh in/màn hình
    LIVENESS_TEXTURE_MIN_VAR = float(os.getenv("LIVENESS_TEXTURE_MIN_VAR", 60.0))

    # ==== Logging (phục vụ đo FPS/Latency cho Chương 4) ====
    LOG_DIR = os.getenv("LOG_DIR", "./logs")


settings = Settings()

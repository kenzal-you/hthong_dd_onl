"""
================================================================================
VAI TRÒ FILE NÀY:
Trích đặc trưng texture (Local Binary Pattern histogram) từ vùng khuôn mặt,
dùng làm input cho classifier chống giả mạo (anti-spoofing) huấn luyện ở
research/train_liveness_svm.py.

HỌC THUẬT / NGHIÊN CỨU Ở ĐÂY:
LBP (Local Binary Pattern) — Ojala et al., 1996/2002 — là đặc trưng texture
cổ điển, KHÔNG học sâu, mô tả cấu trúc bề mặt cục bộ quanh mỗi pixel bằng cách
so sánh với các pixel lân cận.

Ứng dụng cho anti-spoofing dựa trên: Määttä, Hadid & Pietikäinen (2011),
"Face Spoofing Detection From Single Images Using Micro-Texture Analysis",
IJCB 2011 — một trong những công trình nền tảng của hướng texture-based
liveness detection.

Ý tưởng cốt lõi: da mặt thật có texture tự nhiên (lỗ chân lông, phản xạ ánh
sáng không đều), trong khi ảnh in lại hoặc chụp lại từ màn hình thường bị mất
chi tiết tần số cao, hoặc xuất hiện pattern lặp (lưới điểm ảnh, hạt giấy in) —
LBP histogram bắt được sự khác biệt thống kê này, sau đó SVM học ranh giới
phân loại real/spoof từ chính histogram đó.

HẠN CHẾ ĐÃ PHÁT HIỆN QUA THỰC NGHIỆM (và cách khắc phục ở đây):
LBP thô rất nhạy với điều kiện ánh sáng — ảnh thiếu sáng khiến camera tăng ISO
sinh nhiễu hạt, hoặc mất chi tiết tần số cao, biểu hiện GIỐNG HỆT đặc điểm của
ảnh giả bị mất nét (điện thoại/ảnh in). Để giảm phụ thuộc ánh sáng, thêm bước
CLAHE (Contrast Limited Adaptive Histogram Equalization — Zuiderveld, 1994)
chuẩn hoá tương phản cục bộ TRƯỚC khi tính LBP — kỹ thuật tiền xử lý chuẩn,
dùng phổ biến trong các nghiên cứu kết hợp LBP với điều kiện ánh sáng thay đổi.

LƯU Ý QUAN TRỌNG: vì hàm này dùng CHUNG cho cả lúc TRAIN (train_liveness_svm.py)
và lúc DỰ ĐOÁN (liveness.py) nên thay đổi tiền xử lý ở đây tự động áp dụng cho
cả 2 nơi — NHƯNG bắt buộc phải TRAIN LẠI model sau khi sửa file này, vì phân bố
đặc trưng đã thay đổi so với model cũ.
================================================================================
"""

import cv2
import numpy as np
from skimage.feature import local_binary_pattern

_clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))


def extract_lbp_histogram(face_bgr: np.ndarray, P: int = 8, R: int = 1, bins: int = 59) -> np.ndarray:
    """Trích histogram LBP (uniform pattern) từ 1 ảnh khuôn mặt (đã crop).

    P: số điểm lân cận lấy mẫu quanh mỗi pixel
    R: bán kính lấy mẫu
    bins: số bin histogram (59 = số uniform pattern chuẩn với P=8)
    """
    gray = cv2.cvtColor(face_bgr, cv2.COLOR_BGR2GRAY)
    gray = cv2.resize(gray, (128, 128))

    # CLAHE: chuẩn hoá tương phản cục bộ để giảm phụ thuộc vào ánh sáng gốc
    # trước khi trích đặc trưng texture (xem giải thích ở đầu file).
    gray = _clahe.apply(gray)

    lbp = local_binary_pattern(gray, P, R, method="uniform")
    hist, _ = np.histogram(lbp.ravel(), bins=bins, range=(0, bins))

    hist = hist.astype("float")
    hist /= hist.sum() + 1e-7  # chuẩn hoá để không phụ thuộc kích thước ảnh
    return hist

"""
================================================================================
VAI TRÒ FILE NÀY:
"Bộ não" trích xuất đặc trưng khuôn mặt — nhận vào 1 ảnh, trả ra vector số
(embedding) đại diện cho khuôn mặt đó. Đây là thành phần AI cốt lõi của cả
module nhận diện.

HỌC THUẬT / NGHIÊN CỨU Ở ĐÂY (ghi vào báo cáo Chương 1 & 2):
- Detector: SCRFD (Sample and Computation Redistribution for Face Detection),
  tích hợp sẵn trong gói buffalo_l của InsightFace — anchor-free, chạy tốt trên CPU.
- Embedding: ArcFace (backbone ResNet), output vector 512 chiều.
  Paper gốc: Deng et al., "ArcFace: Additive Angular Margin Loss for Deep Face
  Recognition", CVPR 2019.

QUAN TRỌNG - KHÔNG PHẢI TRAINING:
File này chỉ GỌI model đã huấn luyện sẵn (pretrained) để suy luận (inference),
KHÔNG có đoạn code nào train/fine-tune lại ArcFace. Việc "đăng ký" khuôn mặt
sinh viên ở app/api/routes_register.py chỉ là trích embedding rồi lưu vào MySQL
(gọi là ENROLLMENT), không làm thay đổi trọng số của model này.
================================================================================
"""

import numpy as np
from insightface.app import FaceAnalysis

from app.config import settings


class FaceEngine:
    _instance = None

    def __init__(self) -> None:
        self.app = FaceAnalysis(
            name=settings.INSIGHTFACE_MODEL_NAME,
            providers=["CPUExecutionProvider"],
        )
        self.app.prepare(ctx_id=0, det_size=settings.DET_SIZE)

    @classmethod
    def instance(cls) -> "FaceEngine":
        if cls._instance is None:
            cls._instance = FaceEngine()
        return cls._instance

    def extract(self, img: np.ndarray):
        """Trả về danh sách khuôn mặt phát hiện được trong ảnh.
        Mỗi phần tử có: .embedding (512D), .bbox, .det_score, .kps (5 điểm mốc)
        """
        return self.app.get(img)

    def get_primary_face(self, img: np.ndarray):
        """Nếu có nhiều mặt trong khung hình, chọn mặt có diện tích bbox lớn nhất
        (thường là người đang đứng gần camera nhất để điểm danh)."""
        faces = self.extract(img)
        if not faces:
            return None
        faces.sort(
            key=lambda f: (f.bbox[2] - f.bbox[0]) * (f.bbox[3] - f.bbox[1]),
            reverse=True,
        )
        return faces[0]

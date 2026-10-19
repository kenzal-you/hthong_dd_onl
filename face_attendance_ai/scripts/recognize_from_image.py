"""
================================================================================
VAI TRO FILE NAY:
Cong cu TEST TAM THOI - nhan dien khuon mat truc tiep tu 1 file anh co san
(khong qua webcam, KHONG qua liveness check). Dung sau khi da enroll it nhat
1 nguoi bang enroll_from_image.py, de kiem tra rieng phan matching co dung
khong (co nhan ra dung nguoi khong, similarity score bao nhieu).

CACH DUNG:
    python scripts/recognize_from_image.py duong_dan_anh.jpg
    python scripts/recognize_from_image.py duong_dan_anh.jpg 0.35   (tuy chinh threshold)
================================================================================
"""

import os
import sys

import cv2

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from app.config import settings  # noqa: E402
from app.core.face_engine import FaceEngine  # noqa: E402
from app.core.matcher import FaceMatcher  # noqa: E402
from app.db.database import load_all_embeddings  # noqa: E402


def main() -> None:
    if len(sys.argv) not in (2, 3):
        print("Dung: python scripts/recognize_from_image.py <duong_dan_anh> [threshold]")
        return

    image_path = sys.argv[1]
    threshold = float(sys.argv[2]) if len(sys.argv) == 3 else settings.RECOGNITION_THRESHOLD

    if not os.path.exists(image_path):
        print(f"Khong tim thay file: {image_path}")
        return

    img = cv2.imread(image_path)
    if img is None:
        print(f"Khong doc duoc anh: {image_path}")
        return

    print("Dang load model ArcFace...")
    engine = FaceEngine.instance()

    face = engine.get_primary_face(img)
    if face is None:
        print("Khong phat hien duoc khuon mat nao trong anh nay.")
        return

    print(f"Phat hien khuon mat, det_score: {face.det_score:.4f}")

    gallery = load_all_embeddings()
    print(f"So nguoi da dang ky trong MySQL: {len(gallery)}")
    if not gallery:
        print("Gallery rong - hay chay enroll_from_image.py truoc de dang ky it nhat 1 nguoi.")
        return

    matcher = FaceMatcher(threshold=threshold)
    match, score = matcher.match(face.embedding, gallery)

    print(f"\nThreshold dang dung: {threshold}")
    print(f"Similarity cao nhat tim duoc: {score:.4f}")

    if match is None:
        print("KET QUA: Khong nhan dien duoc (similarity duoi threshold).")
    else:
        print(f"KET QUA: Nhan dien la '{match['subject_label']}'")


if __name__ == "__main__":
    main()

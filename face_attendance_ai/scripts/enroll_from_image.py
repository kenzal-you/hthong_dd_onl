"""
================================================================================
VAI TRO FILE NAY:
Cong cu TEST TAM THOI - dang ky khuon mat truc tiep tu 1 file anh co san
(khong qua webcam, KHONG qua liveness check). Dung khi may khong co camera,
de kiem tra rieng phan detect + embedding + luu MySQL co hoat dong dung khong.

LUU Y QUAN TRONG:
Script nay BO QUA hoan toan buoc chong gia mao (liveness). Chi dung de TEST
noi bo trong qua trinh phat trien, KHONG dung thay the cho luong /register
that qua webcam (vi luong that moi co kiem tra chop mat + texture).

CACH DUNG:
    python scripts/enroll_from_image.py duong_dan_anh.jpg SV001
================================================================================
"""

import os
import sys

import cv2

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from app.core.face_engine import FaceEngine  # noqa: E402
from app.db.database import count_subject, save_embedding  # noqa: E402


def main() -> None:
    if len(sys.argv) != 3:
        print("Dung: python scripts/enroll_from_image.py <duong_dan_anh> <subjectLabel>")
        return

    image_path, subject_label = sys.argv[1], sys.argv[2]

    if not os.path.exists(image_path):
        print(f"Khong tim thay file: {image_path}")
        return

    img = cv2.imread(image_path)
    if img is None:
        print(f"Khong doc duoc anh (file hong hoac sai dinh dang): {image_path}")
        return

    print("Dang load model ArcFace...")
    engine = FaceEngine.instance()

    face = engine.get_primary_face(img)
    if face is None:
        print("Khong phat hien duoc khuon mat nao trong anh nay. Thu anh khac ro mat hon.")
        return

    print(f"Phat hien khuon mat, do tin cay (det_score): {face.det_score:.4f}")

    save_embedding(subject_label, face.embedding, face.det_score)
    count = count_subject(subject_label)
    print(f"\nDA DANG KY THANH CONG cho subject '{subject_label}'.")
    print(f"Tong so anh da dang ky cho subject nay: {count}")
    print("Kiem tra lai trong MySQL: SELECT * FROM face_embeddings;")


if __name__ == "__main__":
    main()

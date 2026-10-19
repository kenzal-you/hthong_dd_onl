"""
================================================================================
VAI TRÒ FILE NÀY:
Công cụ THỦ CÔNG để thu thập dữ liệu ảnh real/spoof phục vụ train SVM liveness
ở train_liveness_svm.py. Không phải AI, chỉ là tiện ích chụp ảnh nhanh qua
webcam bằng OpenCV (chạy trực tiếp trên máy có camera, không qua FE web).

CÁCH DÙNG:
    python research/collect_liveness_samples.py real
        -> chụp ảnh mặt thật (chính bạn, bạn bè... nhìn thẳng camera)
    python research/collect_liveness_samples.py spoof
        -> chụp lại ảnh đã IN RA GIẤY, hoặc HIỂN THỊ TRÊN MÀN HÌNH ĐIỆN THOẠI/LAPTOP
           (đưa ra trước webcam rồi chụp)

    Nhấn SPACE để lưu 1 ảnh, nhấn Q để thoát.

KHUYẾN NGHỊ QUY MÔ DỮ LIỆU:
Tối thiểu ~150 ảnh mỗi lớp (real/spoof), càng đa dạng góc mặt/ánh sáng/khoảng
cách càng tốt cho SVM tổng quát hoá. Đây là quy mô hoàn toàn khả thi thu thập
trong 1-2 buổi (mượn thêm vài bạn cùng lớp để có nhiều khuôn mặt real khác nhau
thay vì chỉ 1 người, giúp SVM không học nhầm đặc điểm cá nhân thay vì đặc điểm
real/spoof).
================================================================================
"""

import os
import sys
import time

import cv2


def main() -> None:
    if len(sys.argv) != 2 or sys.argv[1] not in ("real", "spoof"):
        print("Dùng: python research/collect_liveness_samples.py [real|spoof]")
        return

    label = sys.argv[1]
    out_dir = os.path.join("data", "liveness_dataset", label)
    os.makedirs(out_dir, exist_ok=True)

    cap = cv2.VideoCapture(0)
    if not cap.isOpened():
        print("Không mở được webcam.")
        return

    count = len([f for f in os.listdir(out_dir) if f.lower().endswith((".jpg", ".png"))])
    print(f"Đang thu thập lớp '{label}'. Đã có sẵn {count} ảnh. Nhấn SPACE để chụp, Q để thoát.")

    while True:
        ok, frame = cap.read()
        if not ok:
            break

        display = frame.copy()
        cv2.putText(
            display, f"[{label}] da chup: {count}  (SPACE=chup, Q=thoat)",
            (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 0), 2,
        )
        cv2.imshow("Thu thap du lieu liveness", display)

        key = cv2.waitKey(1) & 0xFF
        if key == ord(" "):
            path = os.path.join(out_dir, f"{label}_{int(time.time() * 1000)}.jpg")
            cv2.imwrite(path, frame)
            count += 1
            print(f"Đã lưu {path} (tổng: {count})")
        elif key == ord("q"):
            break

    cap.release()
    cv2.destroyAllWindows()


if __name__ == "__main__":
    main()

## 1. Cấu trúc thư mục 

```
face_attendance_ai/
├── app/
│   ├── main.py                  # Entry point FastAPI, warm-up model lúc khởi động
│   ├── config.py                # Tham số cấu hình (threshold, MySQL, liveness...)
│   ├── api/
│   │   ├── routes_register.py   # POST /register, DELETE /subject/{label}
│   │   └── routes_recognize.py  # POST /recognize
│   ├── core/                     # "Bộ não AI" - tách biệt khỏi HTTP layer
│   │   ├── face_engine.py       # InsightFace (ArcFace) - detect + embedding 512D
│   │   ├── liveness.py          # Hybrid liveness: blink (EAR, log-only) + texture (3 tầng ưu tiên)
│   │   ├── minifas_engine.py    # Wrapper model pretrained MiniFASNet (anti-spoofing chính)
│   │   ├── texture_features.py  # Trích đặc trưng LBP + CLAHE (cho SVM dự phòng)
│   │   └── matcher.py           # Cosine similarity matching
│   ├── db/
│   │   └── database.py          # Lưu trữ embedding trong MySQL + cache gallery RAM
│   ├── schemas/
│   │   └── models.py            # Pydantic request/response
│   └── utils/
│       ├── image_utils.py       # base64 <-> cv2, đo độ nét frame (frame_sharpness)
│       └── logger.py            # Ghi log latency (phục vụ đo FPS/Latency)
├── research/                     # Script OFFLINE cho báo cáo, KHÔNG chạy trong service
│   ├── collect_liveness_samples.py   # Thu thập ảnh real/spoof qua webcam
│   ├── train_liveness_svm.py         # Train SVM anti-spoofing dự phòng (LBP features)
│   ├── prepare_public_dataset.py     # Sắp xếp dataset công khai (vd NUAA) vào đúng cấu trúc
│   ├── build_results_from_images.py  # Sinh results.csv từ ảnh tĩnh (đánh giá nhận diện)
│   └── evaluate_metrics.py           # Tính Accuracy/Precision/Recall/F1/FAR/FRR/EER
├── scripts/                      # Công cụ TEST/DEBUG, không cần webcam hoặc để chẩn đoán
│   ├── enroll_from_image.py          # Đăng ký khuôn mặt từ file ảnh, bỏ qua liveness
│   ├── recognize_from_image.py       # Nhận diện từ file ảnh, bỏ qua liveness
│   ├── build_postman_body.py         # Sinh JSON body (base64) để test qua Postman
│   └── diagnose_svm_scores.py        # Chụp liên tiếp, in phân bố điểm liveness để chẩn đoán
├── models/
│   ├── liveness_svm.pkl         # (tuỳ chọn) SVM dự phòng nếu thiếu model MiniFASNet
│   └── minifas/                 # Model pretrained MiniFASNet (BẮT BUỘC cho Lớp 2 chính)
│       ├── 2.7_80x80_MiniFASNetV2.onnx
│       └── 4_0_0_80x80_MiniFASNetV1SE.onnx
├── data/
│   ├── liveness_dataset/        # real/, spoof/ - chỉ cần nếu muốn train lại SVM dự phòng
│   └── eval_dataset/            # genuine/, impostor/ - dữ liệu đánh giá nhận diện chính
├── logs/
│   └── latency_log.csv          # Tự sinh khi chạy, phục vụ đo Latency/FPS
├── frontend/
│   └── index.html                # FE thực nghiệm (React qua CDN jsdelivr, Babel classic runtime)
├── requirements.txt
├── requirements-research.txt
└── README.md
```

## 2a. Cài đặt (Python 3.10, Windows)

```bash
BE: cd face_attendance_ai

python -m venv venv

venv\Scripts\activate

Cài chương trình: pip install "numpy<2"
pip install -r requirements.txt (Chỉ chạy 1 lần)

```

**Lưu ý cài cần cài **Visual C++ Build Tools** 
Nếu muốn chạy các script trong `research/` (train SVM dự phòng, đánh giá metrics):

```bash
pip install -r requirements-research.txt
```

Bảng `face_embeddings` tự tạo khi server chạy lần đầu. Cấu hình kết nối (host/port/user/password) sửa trong `app/config.py`.

## 2b. Model MiniFASNet 

Model CNN (mạng nơ-ron tích chập) pretrained, đóng vai trò Lớp 2 — chống giả mạo (anti-spoofing/passive liveness) chính thức của module AI. Nằm trong app/core/minifas_engine.py, được gọi từ app/core/liveness.py (texture_ml_check()).

Chức năng cụ thể: nhận vào 1 khung hình + vùng bbox khuôn mặt (lấy từ ArcFace), phân loại ảnh đó thuộc 1 trong 3 nhóm: Real Face (mặt thật) / Paper Photo (ảnh in) / Screen Photo (ảnh trên màn hình điện thoại/laptop) — chặn request nếu không phải mặt thật.

Tên gốc: Silent-Face-Anti-Spoofing, tác giả Minivision AI

Vì sao dùng pretrained thay vì tự train:
CelebA-Spoof (70k+ ảnh, nhiều môi trường) vượt xa quy mô dữ liệu 1 sinh viên tự thu thập được (vài trăm ảnh, 1-2 người, 1 phòng)
Dự án đã thực nghiệm tự train (SVM + LBP) trước đó, phát hiện đúng vấn đề: overfitting, domain gap, nhạy ánh sáng — đây mới là phần nghiên cứu thật sự (quy trình thực nghiệm, không phải con số cuối)
Sau khi so sánh có số liệu đối chứng (SVM dao động 0.18-0.93 vs MiniFASNet ổn định 0.000-0.999), quyết định chuyển sang pretrained là kết luận nghiên cứu có căn cứ, không phải "né tránh train"


## 3. Chạy 

```bash

BE: 
venv\Scripts\activate  
uvicorn app.main:app --reload --port 8000

FE: npm start

```


## 4. Pipeline hoạt động (Hybrid Liveness)

1. FE chụp burst 12 ảnh liên tiếp (~2.4s).
2. **Lớp 1 — Blink (EAR):** tính chuyển động chớp mắt qua MediaPipe. 
3. **Detect + Embedding:** ArcFace trích embedding 512D từ frame tốt nhất.
   - `/register`: chạy trên **toàn bộ 12 frame** (ưu tiên độ chính xác, chấp nhận chậm vì chỉ làm 1 lần/người).
   - `/recognize`: chỉ chạy trên **2 frame nét nhất** (chọn trước bằng `frame_sharpness`, phép đo rẻ không cần AI) — tối ưu tốc độ vì chạy lặp lại liên tục.
4. **Lớp 2 — Passive liveness (BẮT BUỘC, lớp chặn chính):** theo thứ tự ưu tiên:
   - **MiniFASNet** (CNN pretrained, train trên CelebA-Spoof 70k+ ảnh) — nếu có model trong `models/minifas/`
   - SVM + LBP (tự train, dự phòng) — nếu thiếu MiniFASNet nhưng có `models/liveness_svm.pkl`
   - Laplacian variance (đo độ nét, dự phòng cuối) — nếu không có model nào
5. Chỉ khi Lớp 2 pass: `/register` lưu embedding vào MySQL; `/recognize` so khớp cosine similarity với gallery (có cache RAM).
6. Ghi latency từng bước vào `logs/latency_log.csv`.


### 5. Quá trình nghiên cứu thực nghiệm — SVM tự train 

Đây là phần có giá trị nghiên cứu cao nhất để trình bày Chương 3/4/5 — **không phải vì kết quả cuối cùng thắng**, mà vì **quy trình phát hiện lỗi → chẩn đoán → sửa** hoàn chỉnh:

```

1. Train SVM trên data tự chụp (1 người, 1 phòng) → Accuracy 100%
      ↓ (nghi ngờ — quá hoàn hảo)
2. Test thực tế → phát hiện: đổi khoảng cách/tư thế bị từ chối oan
      ↓ (chẩn đoán: overfitting theo môi trường thu thập, không học đúng texture)
3. Chuyển sang bộ dữ liệu chuẩn NUAA Photograph Imposter Database (Tan et al., 2010)
   → Accuracy 91% (thấp hơn nhưng khách quan hơn — dùng research/prepare_public_dataset.py)
      ↓ (test thực tế trên webcam của nhóm)
4. Phát hiện: domain gap — model không quen đặc tính camera thật
      ↓ (giải pháp: domain mixing — gộp NUAA + data tự chụp)
5. Hoạt động đúng khi đủ sáng, nhưng NHẠY ánh sáng yếu
      ↓ (chẩn đoán: LBP nhạy cường độ sáng)
6. Thêm CLAHE (chuẩn hoá tương phản) vào texture_features.py, train lại
      ↓ (test lại nhiều lần — dùng scripts/diagnose_svm_scores.py)
7. Phát hiện: điểm số vẫn dao động lớn (0.18 → 0.93 → 0.58 cho cùng 1 người)
      ↓ (kết luận: đặc trưng thủ công LBP có TRẦN giới hạn về độ ổn định)
8. Chuyển sang MiniFASNet (CNN pretrained, CelebA-Spoof 70k+ ảnh)
      ↓ (test lại)
9. Kết quả: tách biệt rõ ràng, ổn định (real ≈0.999, spoof ≈0.000)
```

**Luận điểm rút ra :** đặc trưng thủ công (hand-crafted features như LBP) có giới hạn về khả năng biểu diễn và độ ổn định khi điều kiện triển khai thay đổi (ánh sáng, thiết bị, khoảng cách); CNN học đặc trưng tự động từ tập dữ liệu lớn, đa dạng cho kết quả vượt trội và ổn định hơn — đúng xu hướng dịch chuyển đã diễn ra trong lịch sử nghiên cứu Computer Vision.

**Cách trả lời nếu hội đồng hỏi "vậy SVM tự train có dùng được không?":**
> *"SVM tự train vẫn hoạt động và được giữ lại làm phương án dự phòng (fallback) trong hệ thống. Qua thực nghiệm so sánh, nhóm nhận thấy đặc trưng thủ công LBP có độ ổn định kém hơn CNN pretrained khi điều kiện triển khai thay đổi — đây là kết luận nghiên cứu có số liệu đối chứng, không phải nhận định chủ quan, và là lý do nhóm chọn MiniFASNet làm lớp chống giả mạo chính thức."*

**Chạy lại thực nghiệm SVM (nếu cần):**
```bash
python research/collect_liveness_samples.py real
python research/collect_liveness_samples.py spoof
python research/prepare_public_dataset.py <thư_mục_NUAA_giải_nén>   # tuỳ chọn, khuyến nghị
python research/train_liveness_svm.py
```

### 6. Vì sao Lớp 1 (blink) không chặn cứng — phân tích đánh đổi bảo mật/trải nghiệm

Đây là quyết định thiết kế có chủ đích, không phải bỏ sót:

- **Lớp 2 (MiniFASNet)** chặn được kiểu tấn công **phổ biến, dễ thực hiện nhất** trong bối cảnh điểm danh sinh viên: ảnh in, ảnh trên màn hình điện thoại — đã kiểm chứng bằng số liệu (real ≈0.999 vs spoof ≈0.000).
- **Lớp 1 (blink)** chỉ chặn thêm được kiểu tấn công **khó và ít khả thi hơn nhiều**: video quay sẵn người khác đang chớp mắt.
- Bắt buộc chớp mắt gây ảnh hưởng lớn đến trải nghiệm (từ chối oan người dùng thật do bỏ lỡ khoảnh khắc nhắm mắt giữa các lần chụp cách nhau 200ms), trong khi lợi ích bảo mật tăng thêm không tương xứng.
- **Quyết định:** giữ Lớp 1 ở chế độ ghi nhận (log), ưu tiên trải nghiệm; vẫn tính toán đầy đủ và trả về trong response để phục vụ giám sát/phân tích sau này, và có thể bật chặn lại qua cấu hình nếu yêu cầu bảo mật hệ thống thay đổi.

**Hạn chế cần nêu (Chương 5):**
- Hệ thống hiện không chống được tấn công video-replay có sẵn cảnh chớp mắt kết hợp chất lượng ảnh cao.
- Hướng phát triển: depth camera, hoặc kết hợp thêm liveness dựa trên chuyển động đầu (active liveness đa dạng hơn).

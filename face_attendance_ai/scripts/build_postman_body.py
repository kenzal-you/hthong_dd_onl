"""
================================================================================
VAI TRO FILE NAY:
Doc mot thu muc anh (da sap xep dung thu tu ten file, vd frame01.jpg,
frame02.jpg...) roi tu dong convert sang base64 va sinh san JSON body
de PASTE THANG vao Postman, test /register hoac /recognize voi day du
liveness that (khong bypass).

CACH DUNG:
    python scripts/build_postman_body.py data/my_frames_folder SV001
    -> in ra JSON, va luu ra file postman_body.json de import/copy

Neu chi muon test /recognize (khong can subjectLabel):
    python scripts/build_postman_body.py data/my_frames_folder
================================================================================
"""

import base64
import glob
import json
import os
import sys


def main() -> None:
    if len(sys.argv) not in (2, 3):
        print("Dung: python scripts/build_postman_body.py <thu_muc_anh> [subjectLabel]")
        return

    folder = sys.argv[1]
    subject_label = sys.argv[2] if len(sys.argv) == 3 else None

    paths = sorted(
        glob.glob(os.path.join(folder, "*.jpg")) + glob.glob(os.path.join(folder, "*.png"))
    )
    if not paths:
        print(f"Khong tim thay anh .jpg/.png nao trong {folder}")
        return

    print(f"Tim thay {len(paths)} anh, theo thu tu:")
    for p in paths:
        print(f"  - {p}")

    frames = []
    for p in paths:
        with open(p, "rb") as f:
            b64 = base64.b64encode(f.read()).decode("utf-8")
            frames.append(f"data:image/jpeg;base64,{b64}")

    body = {"frames": frames}
    if subject_label:
        body = {"subjectLabel": subject_label, "frames": frames}

    out_path = "postman_body.json"
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(body, f)

    print(f"\nDa luu JSON body vao: {out_path}")
    print(f"Kich thuoc file: {os.path.getsize(out_path) / 1024:.1f} KB")
    print("\nMo file nay, copy toan bo noi dung, paste vao Postman (tab Body -> raw -> JSON).")


if __name__ == "__main__":
    main()

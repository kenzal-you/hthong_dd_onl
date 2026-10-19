"""
================================================================================
VAI TRO FILE NAY (BAN CAP NHAT):
Tu dong sap xep anh tu bo dataset NUAA (da tai ve, giai nen) vao dung cau
truc data/liveness_dataset/{train,test}/{real,spoof}/ ma
train_liveness_svm.py (ban cap nhat) can.

THAY DOI QUAN TRONG SO VOI BAN CU:

1) UU TIEN DUNG SPLIT GOC CUA NUAA (client_train_raw.txt, client_test_raw.txt,
   imposter_train_raw.txt, imposter_test_raw.txt) NEU TIM THAY trong thu muc
   giai nen. Day la protocol nghien cuu goc, da duoc thiet ke de tach theo
   NGUOI/SESSION giua train va test -> tu dong tranh duoc data leakage (cung
   1 nguoi vua o train vua o test). KHONG tu chia random nua neu co san file
   nay - vi tu chia random se tron lan session, gay leakage nhu da phan tich.

   Neu KHONG tim thay 4 file .txt nay (vi du ban chi co thu muc ClientRaw/
   ImposterRaw ma khong co list kem theo), script se fallback ve cach cu:
   gom het anh vao 1 pool, danh dau ro subject_id de ban tu split bang
   GroupShuffleSplit trong train_liveness_svm.py (tach theo nguoi, khong
   random tung anh).

2) GIU NGUYEN subject_id VA session TRONG TEN FILE khi copy, thay vi danh
   so lai tu 0. Vi du: ClientRaw/0010/0010_01_05_03_115.jpg
   -> real_0010_01_05_03_115.jpg
   Ly do: neu sau nay ban muon kiem tra lai leakage, hoac muon tu lam
   GroupShuffleSplit theo subject_id, ban CAN con thong tin nay. Doi ten
   thanh real_00001.jpg (ban cu) la xoa vinh vien thong tin nay.

CACH DUNG (khong doi so voi ban cu):
     python research/prepare_public_dataset.py D:\\Downloads\\nuaa_raw

KET QUA:
- Neu tim thay 4 file split goc:
    data/liveness_dataset/train/real/*.jpg
    data/liveness_dataset/train/spoof/*.jpg
    data/liveness_dataset/test/real/*.jpg
    data/liveness_dataset/test/spoof/*.jpg
- Neu KHONG tim thay (fallback):
    data/liveness_dataset/real/*.jpg   (ten file van giu subject_id)
    data/liveness_dataset/spoof/*.jpg
  -> luc nay train_liveness_svm.py se tu chia bang GroupShuffleSplit.
================================================================================
"""

import os
import re
import shutil
import sys

VALID_EXT = (".jpg", ".jpeg", ".png", ".bmp")

REAL_KEYWORDS = ["client", "real", "live", "genuine"]
SPOOF_KEYWORDS = ["imposter", "impostor", "fake", "spoof", "attack", "print"]

DEST_ROOT = os.path.join("data", "liveness_dataset")

# Ten cac file list split goc cua NUAA - co the nam o nhieu bien the ten khac
# nhau tuy nguoi upload len Kaggle, nen liet ke vai bien the pho bien de do tim.
SPLIT_LIST_CANDIDATES = {
    ("real", "train"): ["client_train_raw.txt", "client_train_normalized.txt", "client_train_face.txt"],
    ("real", "test"): ["client_test_raw.txt", "client_test_normalized.txt", "client_test_face.txt"],
    ("spoof", "train"): ["imposter_train_raw.txt", "imposter_train_normalized.txt", "imposter_train_face.txt"],
    ("spoof", "test"): ["imposter_test_raw.txt", "imposter_test_normalized.txt", "imposter_test_face.txt"],
}

# Tach subject_id tu ten file kieu 0010_01_05_03_115.jpg -> "0010"
SUBJECT_ID_RE = re.compile(r"^(\d{4})_")


def classify_path(path: str):
    lower = path.lower()
    if any(k in lower for k in SPOOF_KEYWORDS):
        return "spoof"
    if any(k in lower for k in REAL_KEYWORDS):
        return "real"
    return None


def find_split_lists(src_root: str):
    """Tim 4 file list split goc trong toan bo cay thu muc src_root.
    Tra ve dict {(label, split): duong_dan_file_txt} - chi chua cac cap tim thay.
    """
    found = {}
    all_files = {}
    for dirpath, _, filenames in os.walk(src_root):
        for fname in filenames:
            all_files.setdefault(fname.lower(), os.path.join(dirpath, fname))

    for key, candidates in SPLIT_LIST_CANDIDATES.items():
        for cand in candidates:
            if cand.lower() in all_files:
                found[key] = all_files[cand.lower()]
                break
    return found


def read_list_file(path: str):
    """Doc file .txt liet ke duong dan/tuen file anh, moi dong 1 muc.
    Chi lay ten file (basename) vi duong dan tuyet doi trong file list co the
    khac voi cau truc thuc te sau khi giai nen (vd path Kaggle /kaggle/input/...).
    """
    names = set()
    with open(path, "r", encoding="utf-8", errors="ignore") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            # Lay phan tu cuoi cua duong dan (ten file), bo qua thu muc cha
            basename = os.path.basename(line.replace("\\", "/"))
            if basename:
                names.add(basename.lower())
    return names


def build_filename_index(src_root: str):
    """Quet toan bo src_root, lap chi muc ten_file(lower) -> duong_dan_day_du.
    Dung de tra cuu nhanh khi doi chieu voi noi dung file split list.
    """
    index = {}
    for dirpath, _, filenames in os.walk(src_root):
        for fname in filenames:
            if fname.lower().endswith(VALID_EXT):
                index.setdefault(fname.lower(), os.path.join(dirpath, fname))
    return index


def copy_with_subject_id(src_path: str, dest_dir: str, label: str):
    fname = os.path.basename(src_path)
    ext = os.path.splitext(fname)[1]
    m = SUBJECT_ID_RE.match(fname)
    if m:
        # Giu nguyen subject_id + phan con lai cua ten goc (tru phan mo rong)
        stem = os.path.splitext(fname)[0]
        unique_name = f"{label}_{stem}{ext}"
    else:
        # Khong tim thay pattern subject_id chuan -> giu nguyen ten goc, them prefix
        unique_name = f"{label}_{fname}"

    dest_path = os.path.join(dest_dir, unique_name)
    # Tranh de bi ghi de neu ten trung (hiem khi xay ra vi da co subject_id)
    counter = 1
    base_dest_path = dest_path
    while os.path.exists(dest_path):
        stem, ext2 = os.path.splitext(base_dest_path)
        dest_path = f"{stem}_{counter}{ext2}"
        counter += 1

    shutil.copy2(src_path, dest_path)
    return dest_path


def run_with_official_split(src_root: str, split_lists: dict):
    print("=> Tim thay file split goc cua NUAA - dung dung protocol train/test nay.")
    counts = {"train": {"real": 0, "spoof": 0}, "test": {"real": 0, "spoof": 0}}
    filename_index = build_filename_index(src_root)

    for (label, split), list_path in split_lists.items():
        print(f"  Doc {list_path} ...")
        names = read_list_file(list_path)
        dest_dir = os.path.join(DEST_ROOT, split, label)
        os.makedirs(dest_dir, exist_ok=True)

        not_found = 0
        for name in names:
            src_path = filename_index.get(name)
            if src_path is None:
                not_found += 1
                continue
            copy_with_subject_id(src_path, dest_dir, label)
            counts[split][label] += 1

        if not_found > 0:
            print(f"    Canh bao: {not_found}/{len(names)} ten file trong list khong tim thay anh tuong ung trong {src_root}")

    print("\nKet qua (theo dung split goc NUAA):")
    for split in ["train", "test"]:
        print(f"  {split}: real={counts[split]['real']}, spoof={counts[split]['spoof']}")
    print(f"\nDa luu tai: {os.path.join(DEST_ROOT, 'train')} va {os.path.join(DEST_ROOT, 'test')}")
    print("Buoc tiep theo: train_liveness_svm.py (ban cap nhat) se tu doc tu 2 thu muc nay,")
    print("KHONG tu chia train/test nua vi da co split goc dam bao khong leakage.")


def run_fallback_pool(src_root: str):
    print("=> KHONG tim thay 4 file split goc (client/imposter train/test .txt).")
    print("   Fallback: gom het vao 1 pool, giu subject_id trong ten file de ban")
    print("   tu chia bang GroupShuffleSplit (theo nguoi) trong train_liveness_svm.py.")

    os.makedirs(os.path.join(DEST_ROOT, "real"), exist_ok=True)
    os.makedirs(os.path.join(DEST_ROOT, "spoof"), exist_ok=True)

    counts = {"real": 0, "spoof": 0, "bo_qua": 0}

    for dirpath, _, filenames in os.walk(src_root):
        label = classify_path(dirpath)
        for fname in filenames:
            if not fname.lower().endswith(VALID_EXT):
                continue
            file_label = label or classify_path(fname)
            if file_label is None:
                counts["bo_qua"] += 1
                continue

            src_path = os.path.join(dirpath, fname)
            dest_dir = os.path.join(DEST_ROOT, file_label)
            copy_with_subject_id(src_path, dest_dir, file_label)
            counts[file_label] += 1

    print(f"\nDa copy: real={counts['real']}, spoof={counts['spoof']}, bo qua={counts['bo_qua']}")
    print("CANH BAO QUAN TRONG: vi khong co split goc, train_liveness_svm.py PHAI dung")
    print("GroupShuffleSplit theo subject_id (khong duoc dung train_test_split random),")
    print("neu khong ket qua danh gia se bi 'ao' do leakage giua train/test.")


def main() -> None:
    if len(sys.argv) != 2:
        print("Dung: python research/prepare_public_dataset.py <thu_muc_da_giai_nen>")
        return

    src_root = sys.argv[1]
    if not os.path.isdir(src_root):
        print(f"Khong tim thay thu muc: {src_root}")
        return

    split_lists = find_split_lists(src_root)

    if len(split_lists) == 4:
        run_with_official_split(src_root, split_lists)
    elif len(split_lists) > 0:
        print(f"Chi tim thay {len(split_lists)}/4 file split goc ({list(split_lists.keys())}).")
        print("Can du 4 file (real/spoof x train/test) moi dung duoc che do nay.")
        print("Chuyen sang fallback pool.\n")
        run_fallback_pool(src_root)
    else:
        run_fallback_pool(src_root)


if __name__ == "__main__":
    main()
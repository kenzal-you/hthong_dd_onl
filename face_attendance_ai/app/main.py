"""
================================================================================
VAI TRÒ FILE NÀY:
Điểm khởi động (entry point) của service AI — khởi tạo FastAPI app, đăng ký
các router (/register, /recognize), và "warm-up" model AI ngay khi server start
(để request đầu tiên của user không bị chậm do phải load model).
================================================================================
"""

from fastapi import Depends, FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api import routes_live, routes_recognize, routes_register
from app.core.face_engine import FaceEngine
from app.db.database import get_conn 
from app.core.security import verify_api_key
app = FastAPI(title="Face Attendance AI Service - POC")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)
app.include_router(routes_register.router, tags=["register"], dependencies=[Depends(verify_api_key)])
app.include_router(routes_recognize.router, tags=["recognize"],dependencies=[Depends(verify_api_key)])


app.include_router(routes_live.router, tags=["live-preview"])


@app.on_event("startup")
def warmup() -> None:
    get_conn()
    print("Dang khoi tao InsightFace model (ArcFace)...")
    FaceEngine.instance()
    print("Model da san sang.")


@app.get("/health")
def health():
    return {"status": "ok", "message": "Face Attendance AI service đang chạy"}

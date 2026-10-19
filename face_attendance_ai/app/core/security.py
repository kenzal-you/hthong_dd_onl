from fastapi import Header, HTTPException

from app.config import settings


def verify_api_key(x_api_key: str = Header(None)):
    if x_api_key != settings.INTERNAL_API_KEY:
        raise HTTPException(status_code=401, detail="Unauthorized")
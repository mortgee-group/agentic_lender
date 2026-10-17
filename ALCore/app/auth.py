from fastapi import Header, HTTPException, status
from firebase_admin import auth

import firebase_admin

if not firebase_admin._apps:
    firebase_admin.initialize_app()


def verify_firebase_token(authorization: str | None = Header(default=None)):
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing or invalid Authorization header",
        )

    token = authorization.removeprefix("Bearer ").strip()

    if not token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing Firebase ID token",
        )

    try:
        decoded_token = auth.verify_id_token(token)
        return decoded_token

    except (ValueError, auth.InvalidIdTokenError,
            auth.ExpiredIdTokenError, auth.RevokedIdTokenError):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired Firebase ID token",
        )
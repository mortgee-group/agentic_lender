from fastapi import Depends, HTTPException, status

from app.auth import verify_firebase_token
from app.models import UserRole
from app.users import get_user_profile


def require_role(*allowed_roles: UserRole):
    def check_role(
        decoded_token: dict = Depends(verify_firebase_token),
    ):
        uid = decoded_token["uid"]
        profile = get_user_profile(uid)

        if profile is None:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="User profile not found",
            )

        if profile.role not in allowed_roles:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Insufficient permissions",
            )

        return profile

    return check_role
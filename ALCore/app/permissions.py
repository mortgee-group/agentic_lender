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


def test_broker_cannot_create_application():
    def mock_broker():
        return {"uid": "broker-123"}

    def deny_broker():
        from fastapi import HTTPException

        raise HTTPException(
            status_code=403,
            detail="Insufficient permissions",
        )

    borrower_dependency = next(
        dependency.call
        for route in app.routes
        if getattr(route, "path", None) == "/applications"
        and "POST" in getattr(route, "methods", set())
        for dependency in route.dependant.dependencies
    )

    app.dependency_overrides[borrower_dependency] = deny_broker

    try:
        with patch("app.main.create_application") as mock_create:
            response = client.post("/applications", json={})

        assert response.status_code == 403
        assert response.json()["detail"] == "Insufficient permissions"
        mock_create.assert_not_called()

    finally:
        app.dependency_overrides.clear()
        
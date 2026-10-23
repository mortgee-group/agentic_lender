from app.firebase import db
from app.models import UserProfile


def get_user_profile(uid: str):
    """Retrieve a user profile from Firestore."""
    doc = db.collection("users").document(uid).get()

    if not doc.exists:
        return None

    return UserProfile(**doc.to_dict())


def create_user_profile(profile: UserProfile):
    """Save a new user profile to Firestore."""
    db.collection("users").document(profile.uid).create(
        profile.model_dump(mode="json")
    )

    return profile
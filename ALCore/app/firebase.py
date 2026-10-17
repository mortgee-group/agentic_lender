import firebase_admin
from firebase_admin import firestore


def initialise_firebase():
    try:
        firebase_admin.get_app()
    except ValueError:
        firebase_admin.initialize_app()

    return firestore.client()


db = initialise_firebase()
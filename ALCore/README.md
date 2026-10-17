# AL-Core Backend

This is the backend API for the Agentic Lender project, built with Python and FastAPI.

It handles Firebase Authentication and connects to Cloud Firestore. The API is hosted on Google Cloud Run so the Android and Web teams can use it during development.

## Getting Started

### What you'll need

- Python 3.12
- Git
- Access to the Google Cloud project (only if you're running the backend locally)

### 1. Clone the repository

If you haven't cloned the project yet, run:

```cmd
git clone https://github.com/mortgee-group/agentic_lender.git
```

### 2. Open the backend folder

```cmd
cd agentic_lender\ALCore
```

### 3. Set up a virtual environment

Create a virtual environment:

```cmd
python -m venv .venv
```

Activate it:

```cmd
.venv\Scripts\activate
```

### 4. Install the dependencies

```cmd
python -m pip install -r requirements.txt
```

## Firebase Setup

The backend uses Firebase Authentication and Cloud Firestore.

If you're working on the Android or Web app, you can use the deployed API without setting up backend credentials.

If you want to run the Python backend locally, you'll need access to our Google Cloud project.

Once you have access, you can set up Application Default Credentials using the Google Cloud CLI:

```cmd
gcloud auth application-default login
```

This lets the backend use your Google Cloud account for local development.

Keep any Firebase service account files private and never upload them to GitHub.


## Running the Backend

Once everything is installed and Firebase is configured, start the API:

```cmd
python -m uvicorn app.main:app --reload
```

You can now open:

- **API documentation:** http://127.0.0.1:8000/docs
- **Health check:** http://127.0.0.1:8000/health

The `/docs` page lets you explore and test the available endpoints.

## Running Tests

To run the backend tests, use:

```cmd
python -m pytest -v
```

Make sure you're inside the `ALCore` folder with your virtual environment activated and Firebase credentials configured.

## Available Endpoints

| Method | Endpoint | What it does |
|--------|----------|--------------|
| GET | `/health` | Checks that the backend is running |
| GET | `/auth/me` | Returns the UID of the authenticated Firebase user |

The `/health` endpoint is public.

The `/auth/me` endpoint requires a Firebase ID token in the request header:

```http
Authorization: Bearer <FIREBASE_ID_TOKEN>
```

The Android and Web apps can get this token through the Firebase SDK after a user signs in.

## Development API

You don't need to run the backend locally just to test the API. There's already a development version running on Google Cloud Run.

**API URL:**

https://agentic-lender-525895646792.europe-west2.run.app

**Swagger documentation:**

https://agentic-lender-525895646792.europe-west2.run.app/docs

**Health check:**

https://agentic-lender-525895646792.europe-west2.run.app/health

The Android and Web teams can use this URL to connect to the backend.

## Deployment

The backend is deployed to Google Cloud Run in the `europe-west2` (London) region.

Deployment is connected to the `feature/core-backend` branch, so pushing changes to that branch triggers a new build and deployment through Google Cloud Build.

The Dockerfile is located at `ALCore/Dockerfile`.

On Cloud Run, Firebase uses the service's Google Cloud credentials, so there's no need to upload a service account JSON file.